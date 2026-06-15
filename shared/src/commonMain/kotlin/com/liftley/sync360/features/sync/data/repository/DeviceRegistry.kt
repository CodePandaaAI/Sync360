package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceRegistry {
    private val _approvedSessions = MutableStateFlow<List<ApprovedSessionDevice>>(emptyList())
    private val _approvedDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val approvedDevices: StateFlow<List<DeviceProfile>> = _approvedDevices.asStateFlow()

    fun upsert(device: DeviceProfile, sessionToken: String) {
        val current = _approvedSessions.value
        _approvedSessions.value = if (current.none { it.device.id == device.id }) {
            current + ApprovedSessionDevice(device, sessionToken)
        } else {
            current.map { existing ->
                if (existing.device.id == device.id) {
                    existing.copy(
                        device = device.copy(hostAddress = device.hostAddress ?: existing.device.hostAddress),
                        sessionToken = sessionToken
                    )
                } else {
                    existing
                }
            }
        }
        _approvedDevices.value = _approvedSessions.value.map { it.device }
    }

    fun delete(deviceId: String) {
        _approvedSessions.value = _approvedSessions.value.filterNot { it.device.id == deviceId }
        _approvedDevices.value = _approvedSessions.value.map { it.device }
    }

    fun hasValidSession(deviceId: String, sessionToken: String): Boolean {
        return _approvedSessions.value.any { it.device.id == deviceId && it.sessionToken == sessionToken }
    }

    fun sessionTokenFor(deviceId: String): String? {
        return _approvedSessions.value.firstOrNull { it.device.id == deviceId }?.sessionToken
    }

    fun hostFor(deviceId: String): String? {
        return _approvedSessions.value.firstOrNull { it.device.id == deviceId }?.device?.hostAddress
    }
}

private data class ApprovedSessionDevice(
    val device: DeviceProfile,
    val sessionToken: String
)
