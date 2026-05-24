package com.liftley.sync360.features.sync.domain.model

expect fun createLocalDeviceProfile(
    context: Any?,
    isDesktop: Boolean,
    desktopAddress: String
): DeviceProfile
