package com.liftley.sync360.data.local

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.LocalDeviceInfo
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
class IosLocalDeviceInfoProvider(
    private val deviceUuid: String
) : LocalDeviceInfoProvider {
    private val localDeviceInfo = run {
        val deviceName = UIDevice.currentDevice.name
            .trim()
            .ifBlank { UIDevice.currentDevice.model }

        LocalDeviceInfo(
            deviceId = deviceUuid,
            deviceName = deviceName,
            deviceType = "iOS",
            protocolVersion = "1"
        )
    }

    override fun getLocalDeviceInfo(): LocalDeviceInfo = localDeviceInfo
}
