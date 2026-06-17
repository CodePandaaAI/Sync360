package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview

internal class IncomingTransferSession {
    private var offerId: String? = null
    private var senderDeviceId: String? = null
    private var sessionToken: String? = null
    private var senderName: String = ""
    private var files: List<TransferFilePreview> = emptyList()
    private val savedPathsByIndex = mutableMapOf<Int, String>()

    val savedPaths: List<String>
        get() = savedPathsByIndex.values.toList()

    fun start(
        offerId: String,
        senderDeviceId: String,
        sessionToken: String,
        senderName: String,
        files: List<TransferFilePreview>
    ) {
        this.offerId = offerId
        this.senderDeviceId = senderDeviceId
        this.sessionToken = sessionToken
        this.senderName = senderName
        this.files = files
        savedPathsByIndex.clear()
    }

    fun canReceiveFile(offerId: String, fileIndex: Int): Boolean {
        return offerId == this.offerId &&
            fileIndex in files.indices &&
            fileIndex == savedPathsByIndex.size &&
            fileIndex !in savedPathsByIndex
    }

    fun isCurrentOffer(offerId: String, senderDeviceId: String): Boolean {
        return offerId == this.offerId && senderDeviceId == this.senderDeviceId
    }

    fun isComplete(offerId: String, senderDeviceId: String): Boolean {
        return isCurrentOffer(offerId, senderDeviceId) &&
            files.isNotEmpty() &&
            savedPathsByIndex.size == files.size
    }

    fun hasSessionToken(sessionToken: String): Boolean {
        return sessionToken == this.sessionToken
    }

    fun fileNameAt(fileIndex: Int): String? {
        return files.getOrNull(fileIndex)?.name
    }

    fun fileAt(fileIndex: Int): TransferFilePreview? = files.getOrNull(fileIndex)

    fun completeFile(fileIndex: Int, savedPath: String): ReceivedFileBatch? {
        if (fileIndex !in files.indices || fileIndex in savedPathsByIndex) return null
        savedPathsByIndex[fileIndex] = savedPath
        if (savedPathsByIndex.size != files.size) return null

        return ReceivedFileBatch(
            senderName = senderName,
            files = files,
            savedPaths = files.indices.map { index -> savedPathsByIndex.getValue(index) },
            senderDeviceId = senderDeviceId.orEmpty()
        )
    }

    fun clear() {
        offerId = null
        senderDeviceId = null
        sessionToken = null
        senderName = ""
        files = emptyList()
        savedPathsByIndex.clear()
    }
}
