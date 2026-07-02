package com.liftley.sync360.presentation.send.model

sealed interface TextSendState {
    data object Idle : TextSendState

    data class Sending(
        val deviceName: String,
        val text: String
    ) : TextSendState

    data class Sent(
        val deviceName: String,
        val text: String
    ) : TextSendState

    data class Failed(
        val reason: String
    ) : TextSendState
}