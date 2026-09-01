package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.FileTransferProgress

sealed interface SendState {
    data object Idle : SendState
    data class SendingText(
        val deviceName: String
    ) : SendState

    data class TextSent(
        val deviceName: String
    ) : SendState

    data class PreparingFiles(
        val deviceName: String,
        val fileCount: Int
    ) : SendState

    data class SendingFile(
        val deviceName: String,
        val fileName: String,
        val fileNumber: Int,
        val totalFiles: Int,
        val progress: FileTransferProgress
    ) : SendState

    data class FilesSent(
        val deviceName: String,
        val fileCount: Int
    ) : SendState

    data object Cancelled : SendState

    data class Failed(
        val reason: String
    ) : SendState
}
