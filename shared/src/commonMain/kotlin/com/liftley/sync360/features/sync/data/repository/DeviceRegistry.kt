package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PeerIdentity
import com.liftley.sync360.features.sync.domain.model.PeerRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceRegistry {
    private val _approvedSessions = MutableStateFlow<List<ApprovedSession>>(emptyList())
    val approvedSessions: StateFlow<List<ApprovedSession>> = _approvedSessions.asStateFlow()

    private val _approvedDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val approvedDevices: StateFlow<List<DeviceProfile>> = _approvedDevices.asStateFlow()

    fun upsert(device: DeviceProfile, sessionToken: String) {
        val existing = _approvedSessions.value.firstOrNull { it.identity.deviceId == device.id }
        val route = device.hostAddress?.let { PeerRoute(it, device.port) } ?: existing?.route ?: return
        val approved = ApprovedSession(
            identity = PeerIdentity(device.id, device.name, device.type),
            route = route,
            sessionToken = sessionToken
        )
        _approvedSessions.value = _approvedSessions.value
            .filterNot { it.identity.deviceId == device.id } + approved
        publishDevices()
    }

    fun delete(deviceId: String) {
        _approvedSessions.value =
            _approvedSessions.value.filterNot { it.identity.deviceId == deviceId }
        publishDevices()
    }

    fun hasValidSession(deviceId: String, sessionToken: String): Boolean {
        return sessionFor(deviceId)?.sessionToken == sessionToken
    }

    fun sessionTokenFor(deviceId: String): String? = sessionFor(deviceId)?.sessionToken

    fun routeFor(deviceId: String): PeerRoute? = sessionFor(deviceId)?.route

    fun sessionFor(deviceId: String): ApprovedSession? {
        return _approvedSessions.value.firstOrNull { it.identity.deviceId == deviceId }
    }

    private fun publishDevices() {
        _approvedDevices.value = _approvedSessions.value.map { session ->
            DeviceProfile(
                id = session.identity.deviceId,
                name = session.identity.name,
                type = session.identity.type,
                hostAddress = session.route.host,
                port = session.route.port,
                isOnline = true
            )
        }
    }
}

internal data class ApprovedSession(
    val identity: PeerIdentity,
    val route: PeerRoute,
    val sessionToken: String
)
