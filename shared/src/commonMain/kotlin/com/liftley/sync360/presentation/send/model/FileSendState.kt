package com.liftley.sync360.presentation.send.model

sealed interface FileSendState {
    data object Idle : FileSendState

    data class SendingOffer(
        val deviceName: String,
        val fileCount: Int
    ) : FileSendState

    data class OfferAccepted(
        val deviceName: String,
        val fileCount: Int
    ) : FileSendState

    data class OfferDeclined(
        val deviceName: String
    ) : FileSendState

    data class Failed(
        val reason: String
    ) : FileSendState
}