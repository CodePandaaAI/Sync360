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
    private val platformOperations: PlatformOperations
) {
    private var activeOffer: PreparedIncomingFileOffer? = null
    private val savedPaths = mutableMapOf<Int, String>()

    fun prepareOffer(
        offer: FileOfferDto,
        hasPeerGrant: Boolean,
        hasActiveTransfer: Boolean
    ): PreparedIncomingFileOffer? {
        if (hasActiveTransfer) return null
        if (!hasPeerGrant) return null
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
            files = previews,
            totalBytes = totalBytes
        )
    }

    fun startPreparedOffer(
        prepared: PreparedIncomingFileOffer,
        onProgress: (bytes: Long) -> Unit
    ): IncomingOfferStart {
        activeOffer = prepared
        savedPaths.clear()

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
        val currentOffer = activeOffer ?: return false
        return currentOffer.offerId == complete.offerId && currentOffer.senderDeviceId == complete.senderDeviceId
    }

    fun initRawFileWrite(
        offerId: String,
        fileIndex: Int,
        declaredLength: Long,
        fileIdentifier: String
    ): Boolean {
        val currentOffer = activeOffer ?: return false
        if (currentOffer.offerId != offerId) return false
        if (fileIndex !in currentOffer.files.indices) return false

        val file = currentOffer.files[fileIndex]
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
        val currentOffer = activeOffer ?: return false
        if (currentOffer.offerId != offerId) return false
        if (fileIndex !in currentOffer.files.indices) return false
        return fileTransferManager.writeIncomingFileChunk(offerId, fileIndex, chunk, offset, length)
    }

    fun completeFileWrite(offerId: String, fileIndex: Int): IncomingFileWriteComplete {
        val currentOffer = activeOffer ?: return IncomingFileWriteComplete(savedPath = null, batch = null)
        if (currentOffer.offerId != offerId) return IncomingFileWriteComplete(savedPath = null, batch = null)
        if (fileIndex !in currentOffer.files.indices) return IncomingFileWriteComplete(savedPath = null, batch = null)

        val savedPath = fileTransferManager.completeIncomingFileWrite(offerId, fileIndex)
        var batch: ReceivedFileBatch? = null
        if (savedPath != null) {
            savedPaths[fileIndex] = savedPath
            if (savedPaths.size == currentOffer.files.size) {
                // All files received
                val pathsList = currentOffer.files.indices.map { savedPaths[it] ?: "" }
                batch = ReceivedFileBatch(
                    senderName = currentOffer.senderName,
                    files = currentOffer.files,
                    savedPaths = pathsList,
                    senderDeviceId = currentOffer.senderDeviceId
                )
                activeOffer = null
                savedPaths.clear()
            }
        }
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
        if (savedPaths.isNotEmpty()) {
            fileTransferManager.deleteFiles(savedPaths.values.toList())
        }
        activeOffer = null
        savedPaths.clear()
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
    val files: List<TransferFilePreview>,
    val totalBytes: Long
)

internal data class IncomingFileWriteComplete(
    val savedPath: String?,
    val batch: ReceivedFileBatch?
)

private const val HEX_CHARACTERS = "0123456789abcdefABCDEF"
