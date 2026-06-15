package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.features.sync.data.network.FileTransferManager
import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview

internal class IncomingFileTransferCoordinator(
    private val fileTransferManager: FileTransferManager,
    private val sessionAuthenticator: SessionAuthenticator
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

        val previews = offer.files.map { TransferFilePreview(it.fileName, it.mimeType, it.fileSize) }
        incomingTransferSession.start(
            offerId = offer.offerId,
            senderDeviceId = offer.senderDeviceId,
            sessionToken = offer.sessionToken,
            senderName = offer.senderName,
            files = previews
        )
        fileTransferManager.registerIncomingTotalSize(offer.files.sumOf { it.fileSize }, onProgress)

        return IncomingOfferStart(
            progress = FileTransferProgress(
                peerName = offer.senderName,
                files = previews,
                percent = 1,
                direction = TransferDirection.RECEIVING
            ),
            senderName = offer.senderName,
            fileCount = offer.files.size
        )
    }

    fun completeSignal(complete: FileCompleteDto, isApprovedSession: Boolean): Boolean {
        if (!isApprovedSession) return false
        if (!sessionAuthenticator.verifyFileComplete(complete)) return false
        return incomingTransferSession.isCurrentOffer(complete.offerId, complete.senderDeviceId)
    }

    fun initFileWrite(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields
    ): Boolean {
        if (!incomingTransferSession.canReceiveFile(offerId, fileIndex)) return false
        if (!incomingTransferSession.hasSessionToken(sessionToken)) return false
        if (!sessionAuthenticator.verifyFileUpload(sessionToken, authFields, offerId, fileIndex)) return false

        val fileName = incomingTransferSession.fileNameAt(fileIndex) ?: "file_$fileIndex"
        return fileTransferManager.initIncomingFileWrite(offerId, fileIndex, fileName)
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

    fun clear() {
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
