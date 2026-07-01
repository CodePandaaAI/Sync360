package com.liftley.sync360.domain.model

import com.liftley.sync360.data.remote.client.clientRequest.MessagePayload
import com.liftley.sync360.data.remote.client.clientRequest.OfferRequest

sealed interface ClientServerState {
    data object Idle: ClientServerState
    data class Busy(val offerRequest: OfferRequest): ClientServerState

    data class Received(val data: MessagePayload): ClientServerState
}

enum class UserDecision {
    IDLE, ACCEPTED, DECLINED
}