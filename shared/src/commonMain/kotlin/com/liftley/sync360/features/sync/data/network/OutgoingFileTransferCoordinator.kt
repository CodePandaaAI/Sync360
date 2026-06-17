package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FilePreviewDto
import com.liftley.sync360.features.sync.domain.diagnostics.TransferDiagnostics
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import kotlin.time.TimeSource

class OutgoingFileTransferCoordinator(
    private val localDevice: DeviceProfile,
    private val httpClient: HttpSyncClient,
    private val fileTransferManager: FileTransferManager,
    private val rawTcpFileTransport: RawTcpFileTransport = RawTcpFileTransport()
) {
    fun cancelActiveTransfer() {
        rawTcpFileTransport.closeActiveConnections()
    }



    fun previewsForItems(items: List<SendItem>): List<TransferFilePreview> {
        return items.map { TransferFilePreview(it.displayName, it.mimeType, it.sizeBytes) }
    }


    suspend fun sendItems(
        peerHost: String,
        peerPort: Int,
        offerId: String,
        items: List<SendItem>,
        onProgress: (bytes: Long) -> Unit,
        onVerifying: () -> Unit
    ): FileSendResult {
        val transferStarted = TimeSource.Monotonic.markNow()
        val totalBytes = items.sumOf { it.sizeBytes }
        fun logEndToEnd(outcome: String) {
            TransferDiagnostics.log(
                stage = "sender_transfer_end_to_end",
                bytes = totalBytes,
                elapsedNanos = transferStarted.elapsedNow().inWholeNanoseconds,
                bufferBytes = TRANSFER_BUFFER_BYTES,
                dispatcher = "Repository CoroutineScope(Dispatchers.Default) + nested IO/CIO",
                streamed = true,
                fullFileInMemory = false,
                base64 = false,
                stringEncoding = false,
                json = false,
                multipart = false,
                details = "transferId=$offerId files=${items.size} outcome=$outcome"
            )
        }
        val preparedFiles = fileTransferManager.prepareOutgoingItems(items)
            ?: run {
                logEndToEnd("prepare_failure")
                return FileSendResult.Failure(FileSendFailure.SOURCE_UNAVAILABLE)
            }
        val offer = FileOfferDto(
            offerId = offerId,
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            files = preparedFiles.map {
                FilePreviewDto(it.item.displayName, it.item.mimeType, it.item.sizeBytes, it.sha256)
            }
        )

        val offerStarted = TimeSource.Monotonic.markNow()
        val notified = httpClient.sendFileOffer(peerHost, peerPort, offer)
        val offerAccepted = notified as? FileOfferTransportResult.Accepted
        TransferDiagnostics.log(
            stage = "sender_file_offer_control",
            bytes = 0L,
            elapsedNanos = offerStarted.elapsedNow().inWholeNanoseconds,
            bufferBytes = 0,
            dispatcher = "Ktor CIO client",
            streamed = false,
            fullFileInMemory = false,
            base64 = false,
            stringEncoding = false,
            json = true,
            multipart = false,
            details = "transferId=$offerId files=${items.size}" +
                " outcome=${if (offerAccepted != null) "success" else "failure"}"
        )
        if (offerAccepted == null) {
            logEndToEnd("offer_failure")
            return (notified as FileOfferTransportResult.Failure).transport.toFileSendFailure()
        }

        val endpoint = offerAccepted.response
        val uploaded = fileTransferManager.uploadOutgoingItemsRaw(
            rawTransport = rawTcpFileTransport,
            serverIp = requireNotNull(endpoint.rawTcpHost),
            serverPort = requireNotNull(endpoint.rawTcpPort),
            offerId = offerId,
            transferToken = requireNotNull(endpoint.transferToken),
            items = preparedFiles.map { it.item },
            onProgress = onProgress
        )
        if (uploaded is HttpTransportResult.Failure) {
            logEndToEnd("upload_failure")
            return uploaded.toFileSendFailure()
        }
        onVerifying()

        val completeStarted = TimeSource.Monotonic.markNow()
        val completed = httpClient.sendFileComplete(
            peerHost,
            peerPort,
            FileCompleteDto(
                offerId = offerId,
                senderDeviceId = localDevice.id
            )
        )
        TransferDiagnostics.log(
            stage = "sender_file_complete_control",
            bytes = 0L,
            elapsedNanos = completeStarted.elapsedNow().inWholeNanoseconds,
            bufferBytes = 0,
            dispatcher = "Ktor CIO client",
            streamed = false,
            fullFileInMemory = false,
            base64 = false,
            stringEncoding = false,
            json = true,
            multipart = false,
            details = "transferId=$offerId" +
                " outcome=${if (completed is HttpTransportResult.Success) "success" else "failure"}"
        )
        return if (completed is HttpTransportResult.Success) {
            logEndToEnd("success")
            FileSendResult.Success
        } else {
            logEndToEnd("complete_failure")
            (completed as HttpTransportResult.Failure).toFileSendFailure()
        }
    }

    private companion object {
        const val TRANSFER_BUFFER_BYTES = 1024 * 1024
    }
}

sealed interface FileSendResult {
    data object Success : FileSendResult
    data class Failure(val reason: FileSendFailure) : FileSendResult
}

enum class FileSendFailure {
    SOURCE_UNAVAILABLE,
    REMOTE_STORAGE_FULL,
    REMOTE_STORAGE_UNAVAILABLE,
    INTEGRITY_FAILED,
    TIMED_OUT,
    RECEIVER_UNAVAILABLE,
    RECEIVER_BUSY,
    NETWORK_FAILED,
    TRANSFER_INTERRUPTED,
    RECEIVER_CANCELLED
}

private fun HttpTransportResult.Failure.toFileSendFailure(): FileSendResult.Failure {
    val reason = if (error == HttpTransportError.REJECTED) {
        when (detail) {
            "receiver_declined" -> FileSendFailure.RECEIVER_CANCELLED
            "receiver_busy" -> FileSendFailure.RECEIVER_BUSY
            "sender_not_discovered" -> FileSendFailure.RECEIVER_UNAVAILABLE
            else -> FileSendFailure.TIMED_OUT
        }
    } else {
        when (error) {
            HttpTransportError.SOURCE_READ_FAILED -> FileSendFailure.SOURCE_UNAVAILABLE
            HttpTransportError.REMOTE_STORAGE_FULL -> FileSendFailure.REMOTE_STORAGE_FULL
            HttpTransportError.REMOTE_STORAGE_UNAVAILABLE -> FileSendFailure.REMOTE_STORAGE_UNAVAILABLE
            HttpTransportError.INTEGRITY_FAILED -> FileSendFailure.INTEGRITY_FAILED
            HttpTransportError.TIMEOUT -> FileSendFailure.TIMED_OUT
            HttpTransportError.TRANSFER_INTERRUPTED -> FileSendFailure.TRANSFER_INTERRUPTED
            HttpTransportError.RECEIVER_CANCELLED -> FileSendFailure.RECEIVER_CANCELLED
            HttpTransportError.BUSY -> FileSendFailure.RECEIVER_BUSY
            HttpTransportError.UNREACHABLE -> FileSendFailure.RECEIVER_UNAVAILABLE
            else -> FileSendFailure.NETWORK_FAILED
        }
    }
    return FileSendResult.Failure(reason)
}
