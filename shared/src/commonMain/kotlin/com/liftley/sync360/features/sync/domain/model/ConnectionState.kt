package com.liftley.sync360.features.sync.domain.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class ResolvingRoute(val device: DeviceProfile) : ConnectionState
    data class Requesting(val device: DeviceProfile) : ConnectionState
    data class AwaitingApproval(val device: DeviceProfile) : ConnectionState
    data class Connected(val deviceId: String) : ConnectionState
    data class Disconnecting(val deviceId: String) : ConnectionState
    data class Failed(
        val device: DeviceProfile?,
        val reason: ConnectionFailure
    ) : ConnectionState
}

enum class ConnectionFailure {
    INVALID_HOST,
    MISSING_ROUTE,
    SERVER_UNAVAILABLE,
    UNREACHABLE,
    REQUEST_TIMEOUT,
    APPROVAL_TIMEOUT,
    DECLINED,
    PEER_BUSY,
    PROTOCOL_MISMATCH,
    AUTHENTICATION_FAILED,
    ROUTE_CHANGED,
    CLIENT_CLOSED,
    UNKNOWN
}

data class ConnectionSnapshot(
    val state: ConnectionState = ConnectionState.Idle,
    val pendingIncoming: List<DeviceProfile> = emptyList()
)
