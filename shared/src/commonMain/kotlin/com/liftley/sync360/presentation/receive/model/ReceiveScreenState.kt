package com.liftley.sync360.presentation.receive.model

import com.liftley.sync360.data.network.http.dto.file.FileOfferItem

sealed interface ReceiveScreenState {
    data object Idle : ReceiveScreenState

    data class IncomingTextOffer(
        val senderDeviceName: String,
        val preview: String,
        val characterCount: Int
    ) : ReceiveScreenState

    data class IncomingFileOffer(
        val senderDeviceName: String,
        val files: List<FileOfferItem>,
        val totalSizeBytes: Long?
    ): ReceiveScreenState

    data class ReceivedText(
        val text: String
    ) : ReceiveScreenState
}