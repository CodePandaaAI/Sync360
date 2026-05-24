package com.liftley.sync360.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SyncPayload(
    val kind: String,
    val originDeviceId: String,
    val originDeviceName: String,
    val originDeviceType: String,
    val content: String,
    val timestamp: Long,
    val targetDeviceId: String? = null
)

object SyncPayloadCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: SyncPayload): String = json.encodeToString(SyncPayload.serializer(), payload)

    fun decodeOrNull(frame: String): SyncPayload? {
        return try {
            json.decodeFromString(SyncPayload.serializer(), frame)
        } catch (_: Exception) {
            null
        }
    }
}
