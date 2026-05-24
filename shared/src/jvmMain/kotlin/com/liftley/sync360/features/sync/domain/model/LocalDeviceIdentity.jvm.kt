package com.liftley.sync360.features.sync.domain.model

import java.io.File
import java.net.InetAddress
import java.util.UUID

actual fun createLocalDeviceProfile(
    context: Any?,
    isDesktop: Boolean,
    desktopAddress: String
): DeviceProfile {
    val hostName = runCatching { InetAddress.getLocalHost().hostName }
        .getOrDefault("Desktop")
        .ifBlank { "Desktop" }

    val configDir = File(System.getProperty("user.home"), ".sync360")
    configDir.mkdirs()
    val idFile = File(configDir, "device_uuid")
    val deviceId = if (idFile.exists()) {
        idFile.readText().trim().ifBlank { null }
    } else {
        null
    } ?: UUID.randomUUID().toString().also { idFile.writeText(it) }

    return DeviceProfile(
        id = "desktop-$deviceId",
        name = hostName,
        type = DeviceType.DESKTOP,
        hostAddress = desktopAddress
    )
}
