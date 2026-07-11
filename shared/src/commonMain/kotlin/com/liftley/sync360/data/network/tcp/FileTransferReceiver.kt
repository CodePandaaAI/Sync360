package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.domain.model.FileTransferOffer

interface FileTransferReceiver {
    val port: Int

    suspend fun start()

    fun prepareForTransfer(
        fileOffer: FileTransferOffer,
        onTransferFinished: () -> Unit
    )

    fun clearExpectedTransfer()
}
