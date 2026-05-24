package com.liftley.sync360.features.sync.domain.model

import platform.UIKit.UIDevice

actual fun createLocalDeviceProfile(
    context: Any?,
    isDesktop: Boolean,
    desktopAddress: String
): DeviceProfile {
    val device = UIDevice.currentDevice
    val identifier = device.identifierForVendor?.UUIDString ?: device.name
    return DeviceProfile(
        id = "ios-$identifier",
        name = device.name,
        type = DeviceType.PHONE
    )
}
