package com.liftley.sync360.domain.model

sealed interface ClientServerState {
    data object Idle: ClientServerState
    sealed interface Busy : ClientServerState {
        data class TextOffer(
            val senderDeviceName: String,
            val senderDeviceId: String,
            val preview: String,
            val characterCount: Int
        ) : Busy
    }

    data class Received(val data: String): ClientServerState
}

enum class UserDecision {
    IDLE, ACCEPTED, DECLINED
}