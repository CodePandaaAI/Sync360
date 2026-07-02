package com.liftley.sync360.data.local

import android.content.Context
import androidx.core.content.edit
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AndroidLocalDeviceIdentityStore(context: Context) : LocalDeviceIdentityStore {
    private val prefs = context.getSharedPreferences("sync360_identity", Context.MODE_PRIVATE)

    @OptIn(ExperimentalUuidApi::class)
    override fun getOrCreateDeviceUuid(): String {
        val existing = prefs.getString("device_uuid", null)

        if (existing != null) return existing

        val created = Uuid.random().toString()

        prefs.edit {
            putString("device_uuid", created)
        }

        return created
    }
}