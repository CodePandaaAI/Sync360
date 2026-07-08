package com.liftley.sync360.domain.model

import com.liftley.sync360.data.network.http.dto.file.FileOfferItem

sealed interface ClientServerState {
    data object Idle: ClientServerState
    sealed interface Busy : ClientServerState {
        data class TextOffer(
            val senderDeviceName: String,
            val senderDeviceId: String,
            val preview: String,
            val characterCount: Int
        ) : Busy

        data class FileOffer(
            val senderDeviceId: String,
            val senderDeviceName: String,
            val files: List<FileOfferItem>,
            val totalSizeBytes: Long?
        ): Busy
    }

    data class Received(val data: String): ClientServerState
}

enum class UserDecision {
    IDLE, ACCEPTED, DECLINED
}