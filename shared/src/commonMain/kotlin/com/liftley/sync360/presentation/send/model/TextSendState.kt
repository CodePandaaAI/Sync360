package com.liftley.sync360.presentation.send.model

sealed interface TextSendState {
    data object Idle : TextSendState

    data class SendingOffer(
        val deviceName: String,
        val text: String
    ) : TextSendState

    data class TextSent(
        val deviceName: String,
        val text: String
    ) : TextSendState

    data class OperationFailed(
        val reason: String
    ) : TextSendState
}