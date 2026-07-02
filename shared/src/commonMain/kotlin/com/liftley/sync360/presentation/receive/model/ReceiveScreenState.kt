package com.liftley.sync360.presentation.receive.model

sealed interface ReceiveScreenState {
    data object Idle : ReceiveScreenState

    data class IncomingTextOffer(
        val senderName: String,
        val preview: String,
        val characterCount: Int
    ) : ReceiveScreenState

    data class ReceivedText(
        val text: String
    ) : ReceiveScreenState
}