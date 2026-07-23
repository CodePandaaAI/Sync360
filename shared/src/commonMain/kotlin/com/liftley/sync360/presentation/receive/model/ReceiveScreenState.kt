package com.liftley.sync360.presentation.receive.model

import com.liftley.sync360.domain.model.FileTransferProgress

sealed interface ReceiveScreenState {
    data object Idle : ReceiveScreenState

    data class IncomingTextOffer(
        val senderDeviceName: String,
        val preview: String,
        val characterCount: Int
    ) : ReceiveScreenState

    data class IncomingFileOffer(
        val senderDeviceName: String,
        val fileCount: Int,
        val totalSizeBytes: Long
    ): ReceiveScreenState

    data class ReceivingFiles(
        val senderDeviceName: String,
        val fileCount: Int,
        val completedFileCount: Int,
        val progress: FileTransferProgress
    ) : ReceiveScreenState

    data class ReceivedText(
        val text: String
    ) : ReceiveScreenState

    data class ReceivedFiles(
        val senderDeviceName: String,
        val fileCount: Int
    ) : ReceiveScreenState
}
