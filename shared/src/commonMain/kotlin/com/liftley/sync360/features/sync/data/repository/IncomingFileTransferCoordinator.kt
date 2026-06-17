package com.liftley.sync360.features.sync.data.repository

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
        hasPeerGrant: Boolean,
        hasActiveTransfer: Boolean,
        onProgress: (bytes: Long) -> Unit
    ): IncomingOfferStart? {
        val prepared = prepareOffer(offer, hasPeerGrant, hasActiveTransfer) ?: return null
        return startPreparedOffer(prepared, onProgress)
    }

    fun prepareOffer(
        offer: FileOfferDto,
        hasPeerGrant: Boolean,
        hasActiveTransfer: Boolean
    ): PreparedIncomingFileOffer? {
        if (hasActiveTransfer) return null
        if (!hasPeerGrant) return null
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
        return PreparedIncomingFileOffer(
            offerId = offer.offerId,
            senderDeviceId = offer.senderDeviceId,
            senderName = offer.senderName,
            sessionToken = offer.sessionToken,
            files = previews,
            totalBytes = totalBytes
        )
    }

    fun startPreparedOffer(
        prepared: PreparedIncomingFileOffer,
        onProgress: (bytes: Long) -> Unit
    ): IncomingOfferStart {
        incomingTransferSession.start(
            offerId = prepared.offerId,
            senderDeviceId = prepared.senderDeviceId,
            sessionToken = prepared.sessionToken,
            senderName = prepared.senderName,
            files = prepared.files
        )
        fileTransferManager.registerIncomingTotalSize(prepared.totalBytes, onProgress)

        return IncomingOfferStart(
            progress = FileTransferProgress(
                peerName = prepared.senderName,
                files = prepared.files,
                bytesTransferred = 0L,
                totalBytes = prepared.totalBytes,
                direction = TransferDirection.RECEIVING,
                stage = TransferStage.TRANSFERRING
            ),
            senderName = prepared.senderName,
            fileCount = prepared.files.size
        )
    }

    fun completeSignal(complete: FileCompleteDto, hasPeerGrant: Boolean): Boolean {
        if (!hasPeerGrant) return false
        if (!sessionAuthenticator.verifyFileComplete(complete)) return false
        return incomingTransferSession.isComplete(complete.offerId, complete.senderDeviceId)
    }

    fun initRawFileWrite(
        offerId: String,
        fileIndex: Int,
        declaredLength: Long,
        fileIdentifier: String
    ): Boolean {
        if (!incomingTransferSession.canReceiveFile(offerId, fileIndex)) return false

        val file = incomingTransferSession.fileAt(fileIndex) ?: return false
        if (declaredLength != file.sizeBytes || fileIdentifier != file.name) return false
        val expectedSha256 = file.sha256 ?: return false
        return fileTransferManager.initIncomingFileWrite(
            offerId = offerId,
            fileIndex = fileIndex,
            fileName = file.name,
            mimeType = file.mimeType,
            expectedBytes = file.sizeBytes,
            expectedSha256 = expectedSha256,
            dispatcher = "Dispatchers.IO raw TCP receiver"
        )
    }

    fun writeChunk(offerId: String, fileIndex: Int, chunk: ByteArray, offset: Int, length: Int): Boolean {
        if (!incomingTransferSession.canReceiveFile(offerId, fileIndex)) return false
        return fileTransferManager.writeIncomingFileChunk(offerId, fileIndex, chunk, offset, length)
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
        val paths = incomingTransferSession.savedPaths
        if (paths.isNotEmpty()) {
            fileTransferManager.deleteFiles(paths)
        }
        incomingTransferSession.clear()
    }
}

internal data class IncomingOfferStart(
    val progress: FileTransferProgress,
    val senderName: String,
    val fileCount: Int
)

internal data class PreparedIncomingFileOffer(
    val offerId: String,
    val senderDeviceId: String,
    val senderName: String,
    val sessionToken: String,
    val files: List<TransferFilePreview>,
    val totalBytes: Long
)

internal data class IncomingFileWriteComplete(
    val savedPath: String?,
    val batch: ReceivedFileBatch?
)

private const val HEX_CHARACTERS = "0123456789abcdefABCDEF"
