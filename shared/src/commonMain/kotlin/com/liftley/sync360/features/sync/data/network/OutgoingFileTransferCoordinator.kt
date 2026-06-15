package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.security.SessionAuth
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FilePreviewDto
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview

class OutgoingFileTransferCoordinator(
    private val localDevice: DeviceProfile,
    private val httpClient: HttpSyncClient,
    private val fileTransferManager: FileTransferManager
) {
    fun previews(files: List<PickedFile>): List<TransferFilePreview> {
        return files.map { TransferFilePreview(it.name, it.mimeType, it.sizeBytes) }
    }

    suspend fun sendFiles(
        peerHost: String,
        peerPort: Int,
        offerId: String,
        files: List<PickedFile>,
        sessionToken: String,
        onProgress: (percent: Int) -> Unit,
        onVerifying: () -> Unit
    ): FileSendResult {
        val preparedFiles = fileTransferManager.prepareOutgoingFiles(files)
            ?: return FileSendResult.Failure(FileSendFailure.SOURCE_UNAVAILABLE)
        val offerAuth = SessionAuth.create(
            sessionToken = sessionToken,
            purpose = "file_offer",
            parts = fileOfferAuthParts(offerId, localDevice.id, localDevice.name, preparedFiles)
        )
        val offer = FileOfferDto(
            offerId = offerId,
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            files = preparedFiles.map {
                FilePreviewDto(it.file.name, it.file.mimeType, it.file.sizeBytes, it.sha256)
            },
            sessionToken = sessionToken,
            issuedAtMillis = offerAuth.issuedAtMillis,
            nonce = offerAuth.nonce,
            signature = offerAuth.signature
        )

        val notified = httpClient.sendFileOffer(peerHost, peerPort, offer)
        if (notified is HttpTransportResult.Failure) return notified.toFileSendFailure()

        val uploaded = fileTransferManager.uploadOutgoingFiles(
            peerHost,
            peerPort,
            offerId,
            preparedFiles.map { it.file },
            sessionToken,
            onProgress
        )
        if (uploaded is HttpTransportResult.Failure) return uploaded.toFileSendFailure()
        onVerifying()

        val completeAuth = SessionAuth.create(
            sessionToken = sessionToken,
            purpose = "file_complete",
            parts = listOf(offerId, localDevice.id)
        )
        val completed = httpClient.sendFileComplete(
            peerHost,
            peerPort,
            FileCompleteDto(
                offerId = offerId,
                senderDeviceId = localDevice.id,
                sessionToken = sessionToken,
                issuedAtMillis = completeAuth.issuedAtMillis,
                nonce = completeAuth.nonce,
                signature = completeAuth.signature
            )
        )
        return if (completed is HttpTransportResult.Success) {
            FileSendResult.Success
        } else {
            (completed as HttpTransportResult.Failure).toFileSendFailure()
        }
    }

    private fun fileOfferAuthParts(
        offerId: String,
        senderDeviceId: String,
        senderName: String,
        files: List<PreparedOutgoingFile>
    ): List<String> {
        return listOf(offerId, senderDeviceId, senderName) +
            files.flatMap { prepared ->
                val file = prepared.file
                listOf(file.name, file.mimeType, file.sizeBytes.toString(), prepared.sha256)
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
    NETWORK_FAILED
}

private fun HttpTransportResult.Failure.toFileSendFailure(): FileSendResult.Failure {
    val reason = when (error) {
        HttpTransportError.SOURCE_READ_FAILED -> FileSendFailure.SOURCE_UNAVAILABLE
        HttpTransportError.REMOTE_STORAGE_FULL -> FileSendFailure.REMOTE_STORAGE_FULL
        HttpTransportError.REMOTE_STORAGE_UNAVAILABLE -> FileSendFailure.REMOTE_STORAGE_UNAVAILABLE
        HttpTransportError.INTEGRITY_FAILED -> FileSendFailure.INTEGRITY_FAILED
        else -> FileSendFailure.NETWORK_FAILED
    }
    return FileSendResult.Failure(reason)
}
