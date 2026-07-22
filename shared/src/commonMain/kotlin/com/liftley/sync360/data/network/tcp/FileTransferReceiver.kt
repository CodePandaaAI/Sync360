package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.domain.model.FileTransferProgress

interface FileTransferReceiver {
    val port: Int

    suspend fun start()

    fun prepareForTransfer(
        fileOffer: FileOfferRequest,
        onFileSaved: (completedFileCount: Int) -> Unit,
        onProgress: (FileTransferProgress) -> Unit,
        onTransferFinished: (wasSuccessful: Boolean) -> Unit
    )

    fun clearExpectedTransfer()
}
