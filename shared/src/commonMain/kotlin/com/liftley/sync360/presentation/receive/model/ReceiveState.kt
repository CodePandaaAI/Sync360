package com.liftley.sync360.presentation.receive.model

import com.liftley.sync360.domain.model.FileTransferProgress

sealed interface ReceiveState {
    data class Idle(
        val fileReceiveCode: String
    ) : ReceiveState

    data class ReceivedText(
        val senderDeviceName: String,
        val text: String
    ) : ReceiveState

    data class ReceivingFiles(
        val senderDeviceName: String,
        val fileCount: Int,
        val completedFileCount: Int,
        val progress: FileTransferProgress
    ) : ReceiveState

    data class ReceivedFiles(
        val senderDeviceName: String,
        val fileCount: Int
    ) : ReceiveState
}
