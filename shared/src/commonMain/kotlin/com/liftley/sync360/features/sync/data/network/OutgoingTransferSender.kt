package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FilePreviewDto
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview

class OutgoingTransferSender(
    private val localDevice: DeviceProfile,
    private val httpClient: HttpSyncClient,
    private val transferPayloadStore: TransferPayloadStore,
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
        val preparedItems = transferPayloadStore.prepareOutgoingItems(items)
            ?: return FileSendResult.Failure(FileSendFailure.SOURCE_UNAVAILABLE)

        val offerResult = httpClient.sendFileOffer(
            peerHost,
            peerPort,
            FileOfferDto(
                offerId = offerId,
                senderDeviceId = localDevice.id,
                senderName = localDevice.name,
                files = preparedItems.map { prepared ->
                    FilePreviewDto(
                        fileName = prepared.item.displayName,
                        mimeType = prepared.item.mimeType,
                        fileSize = prepared.item.sizeBytes,
                        sha256 = prepared.sha256
                    )
                }
            )
        )
        val acceptedOffer = offerResult as? FileOfferTransportResult.Accepted
            ?: return (offerResult as FileOfferTransportResult.Failure).transport.toFileSendFailure()

        val endpoint = acceptedOffer.response
        val uploadResult = transferPayloadStore.uploadOutgoingItemsRaw(
            rawTransport = rawTcpFileTransport,
            serverIp = requireNotNull(endpoint.rawTcpHost),
            serverPort = requireNotNull(endpoint.rawTcpPort),
            offerId = offerId,
            transferToken = requireNotNull(endpoint.transferToken),
            items = preparedItems.map { it.item },
            onProgress = onProgress
        )
        if (uploadResult is HttpTransportResult.Failure) {
            return uploadResult.toFileSendFailure()
        }

        onVerifying()
        val completeResult = httpClient.sendFileComplete(
            peerHost,
            peerPort,
            FileCompleteDto(
                offerId = offerId,
                senderDeviceId = localDevice.id
            )
        )
        return if (completeResult is HttpTransportResult.Success) {
            FileSendResult.Success
        } else {
            (completeResult as HttpTransportResult.Failure).toFileSendFailure()
        }
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
