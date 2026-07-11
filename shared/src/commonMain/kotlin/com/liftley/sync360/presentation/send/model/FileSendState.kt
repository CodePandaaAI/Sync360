package com.liftley.sync360.presentation.send.model

sealed interface FileSendState {
    data object Idle : FileSendState

    data class SendingOffer(
        val deviceName: String,
        val fileCount: Int
    ) : FileSendState

    data class SendingFile(
        val deviceName: String,
        val fileName: String,
        val fileNumber: Int,
        val totalFiles: Int
    ) : FileSendState

    data class FilesSent(
        val deviceName: String,
        val fileCount: Int
    ) : FileSendState

    data class OperationFailed(
        val reason: String
    ) : FileSendState
}
