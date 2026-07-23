package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.FileTransferProgress

sealed interface SendOperationState {
    data object Idle : SendOperationState

    data class SendingTextOffer(
        val deviceName: String
    ) : SendOperationState

    data class SendingFileOffer(
        val deviceName: String,
        val fileCount: Int
    ) : SendOperationState

    data class SendingFile(
        val deviceName: String,
        val fileName: String,
        val fileNumber: Int,
        val totalFiles: Int,
        val progress: FileTransferProgress
    ) : SendOperationState

    data class TextSent(
        val deviceName: String
    ) : SendOperationState

    data class FilesSent(
        val deviceName: String,
        val fileCount: Int
    ) : SendOperationState

    data object Cancelled : SendOperationState

    data class OperationFailed(
        val reason: String
    ) : SendOperationState
}
