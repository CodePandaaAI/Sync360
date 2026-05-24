package com.liftley.sync360.features.sync.domain.model

data class DeviceProfile(
    val id: String,
    val name: String,
    val type: DeviceType,
    val hostAddress: String? = null,
    val isOnline: Boolean = true
) {
    /** IP or hostname used for WebSocket connections. */
    val connectionHost: String
        get() = hostAddress?.takeIf { it.isNotBlank() } ?: id
}

enum class DeviceType {
    DESKTOP,
    PHONE,
    TABLET
}
