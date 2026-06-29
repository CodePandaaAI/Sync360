package com.liftley.sync360.domain.model

sealed interface ServerState {
    data object Idle: ServerState
    data class Busy(val requestType: RequestType): ServerState
}

sealed interface RequestType {
    data object Ping: RequestType
}

enum class UserDecision {
    IDLE, ACCEPTED, DECLINED
}