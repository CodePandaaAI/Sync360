package com.liftley.sync360.features.sync.domain.model

sealed interface ConnectionEvent {
    data class WaitingForApproval(val deviceName: String) : ConnectionEvent
    data class Connected(val deviceName: String) : ConnectionEvent
    data class Failed(
        val reason: UserFacingFailure,
        val peer: String? = null
    ) : ConnectionEvent
}

enum class UserFacingFailure {
    SERVER_UNAVAILABLE,
    INVALID_HOST,
    MISSING_ROUTE,
    UNREACHABLE,
    REQUEST_TIMEOUT,
    APPROVAL_TIMEOUT,
    DECLINED,
    PEER_BUSY,
    PROTOCOL_MISMATCH,
    CLIENT_CLOSED,
    TEXT_INVALID,
    TEXT_DELIVERY_FAILED,
    UNKNOWN
}
