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
        offerId: String,
        files: List<PickedFile>,
        sessionToken: String,
        onProgress: (percent: Int) -> Unit
    ): Boolean {
        val offerAuth = SessionAuth.create(
            sessionToken = sessionToken,
            purpose = "file_offer",
            parts = fileOfferAuthParts(offerId, localDevice.id, localDevice.name, files)
        )
        val offer = FileOfferDto(
            offerId = offerId,
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            files = files.map { FilePreviewDto(it.name, it.mimeType, it.sizeBytes) },
            sessionToken = sessionToken,
            issuedAtMillis = offerAuth.issuedAtMillis,
            nonce = offerAuth.nonce,
            signature = offerAuth.signature
        )

        val notified = httpClient.sendFileOffer(peerHost, offer)
        if (!notified) return false

        val uploaded = fileTransferManager.uploadOutgoingFiles(peerHost, offerId, files, sessionToken, onProgress)
        if (!uploaded) return false

        val completeAuth = SessionAuth.create(
            sessionToken = sessionToken,
            purpose = "file_complete",
            parts = listOf(offerId, localDevice.id)
        )
        return httpClient.sendFileComplete(
            peerHost,
            FileCompleteDto(
                offerId = offerId,
                senderDeviceId = localDevice.id,
                sessionToken = sessionToken,
                issuedAtMillis = completeAuth.issuedAtMillis,
                nonce = completeAuth.nonce,
                signature = completeAuth.signature
            )
        )
    }

    private fun fileOfferAuthParts(
        offerId: String,
        senderDeviceId: String,
        senderName: String,
        files: List<PickedFile>
    ): List<String> {
        return listOf(offerId, senderDeviceId, senderName) +
            files.flatMap { file -> listOf(file.name, file.mimeType, file.sizeBytes.toString()) }
    }
}
