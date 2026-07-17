package com.liftley.sync360.data.local

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.LocalDeviceInfo
import java.net.InetAddress

class JvmLocalDeviceInfoProvider(
    private val deviceUuid: String
) : LocalDeviceInfoProvider {
    override fun getLocalDeviceInfo(): LocalDeviceInfo {
        val hostName = runCatching {
            InetAddress.getLocalHost().hostName
        }.getOrNull()

        return LocalDeviceInfo(
            deviceId = deviceUuid,
            deviceName = hostName?.takeIf { it.isNotBlank() } ?: "Desktop",
            deviceType = "Desktop",
            protocolVersion = "1"
        )
    }
}
