package com.liftley.sync360.features.sync.domain.model

data class PeerIdentity(
    val deviceId: String,
    val name: String,
    val type: DeviceType
)

data class PeerRoute(
    val host: String,
    val port: Int
)

sealed interface SessionSnapshot {
    data object NoSession : SessionSnapshot
    data class Approved(
        val identity: PeerIdentity,
        val route: PeerRoute,
        val securityMode: SessionSecurityMode
    ) : SessionSnapshot
}

enum class SessionSecurityMode {
    TRUSTED_LAN_PLAINTEXT
}
