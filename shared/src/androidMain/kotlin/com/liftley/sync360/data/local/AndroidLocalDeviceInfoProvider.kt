package com.liftley.sync360.data.local

import android.os.Build
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.LocalDeviceInfo

class AndroidLocalDeviceInfoProvider(private val deviceUuid: String) : LocalDeviceInfoProvider {
    override fun getLocalDeviceInfo(): LocalDeviceInfo {
        val rawManufacturer = Build.MANUFACTURER.trim()
        val rawModel = Build.MODEL.trim()

        // Capitalize the first letter of the manufacturer safely
        val manufacturer = rawManufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        // Avoiding names like "Google Google Pixel 6" if the model already contains the brand
        val cleanDeviceName = if (rawModel.startsWith(rawManufacturer, ignoreCase = true)) {
            rawModel
        } else {
            "$manufacturer $rawModel"
        }

        return LocalDeviceInfo(
            deviceId = deviceUuid,
            deviceName = cleanDeviceName,
            deviceType = "Android",
            protocolVersion = "1"
        )
    }
}