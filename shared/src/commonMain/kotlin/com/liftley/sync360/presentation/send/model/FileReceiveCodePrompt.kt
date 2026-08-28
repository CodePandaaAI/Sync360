package com.liftley.sync360.presentation.send.model

data class FileReceiveCodePrompt(
    val deviceId: String,
    val deviceName: String,
    val fileCount: Int,
    val code: String = ""
) {
    val sendButtonLabel: String
        get() = if (fileCount == 1) "Send 1 file" else "Send $fileCount files"
}
