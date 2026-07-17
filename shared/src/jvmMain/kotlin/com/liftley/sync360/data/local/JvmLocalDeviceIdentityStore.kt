package com.liftley.sync360.data.local

import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import java.util.UUID
import java.util.prefs.Preferences

class JvmLocalDeviceIdentityStore : LocalDeviceIdentityStore {
    private val preferences = Preferences.userRoot().node("com/liftley/sync360")

    override fun getOrCreateDeviceUuid(): String {
        val existing = preferences.get(DEVICE_UUID_KEY, null)
        if (existing != null) return existing

        val created = UUID.randomUUID().toString()
        preferences.put(DEVICE_UUID_KEY, created)
        return created
    }

    private companion object {
        const val DEVICE_UUID_KEY = "device_uuid"
    }
}
