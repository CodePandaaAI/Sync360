package com.liftley.sync360.presentation.send.model

data class FileReceiveCodePrompt(
    val deviceId: String,
    val deviceName: String,
    val code: String = ""
)
