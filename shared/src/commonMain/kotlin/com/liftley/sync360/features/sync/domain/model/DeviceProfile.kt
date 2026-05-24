package com.liftley.sync360.features.sync.domain.model

data class DeviceProfile(
    val id: String,
    val name: String,
    val type: DeviceType,
    val isOnline: Boolean = true
)

enum class DeviceType {
    DESKTOP,
    PHONE,
    TABLET
}
