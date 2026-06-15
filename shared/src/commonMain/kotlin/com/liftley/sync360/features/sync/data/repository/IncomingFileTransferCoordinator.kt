package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.data.network.FileTransferManager
import com.liftley.sync360.features.sync.data.network.IncomingUploadFailure
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.model.TransferStage

internal class IncomingFileTransferCoordinator(
    private val fileTransferManager: FileTransferManager,
    private val sessionAuthenticator: SessionAuthenticator,
    private val platformOperations: PlatformOperations
) {
    private val incomingTransferSession = IncomingTransferSession()

    fun startOffer(
        offer: FileOfferDto,
        isApprovedSession: Boolean,
        hasActiveTransfer: Boolean,
        onProgress: (percent: Int) -> Unit
    ): IncomingOfferStart? {
        if (hasActiveTransfer) return null
        if (!isApprovedSession) return null
        if (!sessionAuthenticator.verifyFileOffer(offer)) return null
        if (offer.files.isEmpty() || offer.files.size > SyncProtocolLimits.MAX_FILES_PER_TRANSFER) return null

        if (offer.files.any {
                it.fileSize < 0L ||
                    it.fileSize > SyncProtocolLimits.MAX_FILE_BYTES ||
                    it.fileName.isBlank() ||
                    it.fileName.length > SyncProtocolLimits.MAX_FILE_NAME_LENGTH ||
                    it.mimeType.length > SyncProtocolLimits.MAX_MIME_TYPE_LENGTH ||
                    it.sha256.length != SyncProtocolLimits.SHA_256_HEX_LENGTH ||
                    it.sha256.any { char -> char !in HEX_CHARACTERS }
            }
        ) return null
        var totalBytes = 0L
        for (file in offer.files) {
            if (file.fileSize > Long.MAX_VALUE - totalBytes) return null
            totalBytes += file.fileSize
            if (totalBytes > SyncProtocolLimits.MAX_BATCH_BYTES) return null
        }
        val availableBytes = (
            platformOperations.getAvailableStorageBytes() as? FileOperationResult.Success<*>
            )?.value as? Long ?: return null
        if (
            totalBytes > availableBytes ||
            availableBytes - totalBytes < SyncProtocolLimits.MIN_STORAGE_RESERVE_BYTES
        ) return null

        val previews = offer.files.map {
            TransferFilePreview(it.fileName, it.mimeType, it.fileSize, it.sha256)
        }
        incomingTransferSession.start(
            offerId = offer.offerId,
            senderDeviceId = offer.senderDeviceId,
            sessionToken = offer.sessionToken,
            senderName = offer.senderName,
            files = previews
        )
        fileTransferManager.registerIncomingTotalSize(totalBytes, onProgress)

        return IncomingOfferStart(
            progress = FileTransferProgress(
                peerName = offer.senderName,
                files = previews,
                percent = 1,
                direction = TransferDirection.RECEIVING,
                stage = TransferStage.TRANSFERRING
            ),
            senderName = offer.senderName,
            fileCount = offer.files.size
        )
    }

    fun completeSignal(complete: FileCompleteDto, isApprovedSession: Boolean): Boolean {
        if (!isApprovedSession) return false
        if (!sessionAuthenticator.verifyFileComplete(complete)) return false
        return incomingTransferSession.isComplete(complete.offerId, complete.senderDeviceId)
    }

    fun initFileWrite(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields,
        declaredLength: Long
    ): Boolean {
        if (!incomingTransferSession.canReceiveFile(offerId, fileIndex)) return false
        if (!incomingTransferSession.hasSessionToken(sessionToken)) return false
        if (!sessionAuthenticator.verifyFileUpload(sessionToken, authFields, offerId, fileIndex)) return false

        val file = incomingTransferSession.fileAt(fileIndex) ?: return false
        if (declaredLength != file.sizeBytes) return false
        val expectedSha256 = file.sha256 ?: return false
        return fileTransferManager.initIncomingFileWrite(
            offerId = offerId,
            fileIndex = fileIndex,
            fileName = file.name,
            expectedBytes = file.sizeBytes,
            expectedSha256 = expectedSha256
        )
    }

    fun writeChunk(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean {
        if (!incomingTransferSession.canReceiveFile(offerId, fileIndex)) return false
        return fileTransferManager.writeIncomingFileChunk(offerId, fileIndex, chunk)
    }

    fun completeFileWrite(offerId: String, fileIndex: Int): IncomingFileWriteComplete {
        if (!incomingTransferSession.canReceiveFile(offerId, fileIndex)) {
            return IncomingFileWriteComplete(savedPath = null, batch = null)
        }

        val savedPath = fileTransferManager.completeIncomingFileWrite(offerId, fileIndex)
        val batch = savedPath?.let { incomingTransferSession.completeFile(fileIndex, it) }
        return IncomingFileWriteComplete(savedPath = savedPath, batch = batch)
    }

    fun errorFileWrite(offerId: String, fileIndex: Int) {
        fileTransferManager.errorIncomingFileWrite(offerId, fileIndex)
    }

    fun consumeFileWriteFailure(offerId: String, fileIndex: Int): IncomingUploadFailure? {
        return fileTransferManager.consumeIncomingFailure(offerId, fileIndex)
    }

    fun clear() {
        fileTransferManager.cancelAllIncomingWrites()
        incomingTransferSession.clear()
    }
}

internal data class IncomingOfferStart(
    val progress: FileTransferProgress,
    val senderName: String,
    val fileCount: Int
)

internal data class IncomingFileWriteComplete(
    val savedPath: String?,
    val batch: ReceivedFileBatch?
)

private const val HEX_CHARACTERS = "0123456789abcdefABCDEF"
