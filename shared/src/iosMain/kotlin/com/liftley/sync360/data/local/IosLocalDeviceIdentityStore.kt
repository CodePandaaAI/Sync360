package com.liftley.sync360.data.local

import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

@OptIn(ExperimentalForeignApi::class)
class IosLocalDeviceIdentityStore : LocalDeviceIdentityStore {
    private val userDefaults = NSUserDefaults.standardUserDefaults

    override fun getOrCreateDeviceUuid(): String {
        val existingDeviceUuid = userDefaults.stringForKey(DEVICE_UUID_KEY)
        if (existingDeviceUuid != null) return existingDeviceUuid

        val createdDeviceUuid = NSUUID().UUIDString
        userDefaults.setObject(createdDeviceUuid, forKey = DEVICE_UUID_KEY)

        return createdDeviceUuid
    }

    private companion object {
        const val DEVICE_UUID_KEY = "sync360_device_uuid"
    }
}
