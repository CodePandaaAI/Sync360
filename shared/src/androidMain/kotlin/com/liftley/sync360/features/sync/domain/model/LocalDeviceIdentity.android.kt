package com.liftley.sync360.features.sync.domain.model

import android.content.Context
import android.os.Build

actual fun createLocalDeviceProfile(
    context: Any?,
    isDesktop: Boolean,
    desktopAddress: String
): DeviceProfile {
    val androidContext = context as? Context
    val sharedPrefs = androidContext?.getSharedPreferences("sync360_prefs", Context.MODE_PRIVATE)
    var deviceId = sharedPrefs?.getString("device_uuid", null)
    if (deviceId == null && androidContext != null) {
        deviceId = java.util.UUID.randomUUID().toString()
        sharedPrefs?.edit()?.putString("device_uuid", deviceId)?.apply()
    }
    val finalId = deviceId ?: "android-fallback-${Build.MODEL.hashCode().toUInt()}"
    
    val model = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }

    return DeviceProfile(
        id = "android-$finalId",
        name = model,
        type = DeviceType.PHONE
    )
}
