package com.liftley.sync360.features.sync.domain.model

import java.net.InetAddress

actual fun createLocalDeviceProfile(
    context: Any?,
    isDesktop: Boolean,
    desktopAddress: String
): DeviceProfile {
    val hostName = runCatching { InetAddress.getLocalHost().hostName }
        .getOrDefault("Desktop")
        .ifBlank { "Desktop" }

    return DeviceProfile(
        id = desktopAddress,
        name = hostName,
        type = DeviceType.DESKTOP
    )
}
