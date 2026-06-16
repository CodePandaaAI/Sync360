package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PeerIdentity
import com.liftley.sync360.features.sync.domain.model.PeerRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceRegistry {
    // Peer grants are internal route + token records. They are not user-facing
    // connected sessions; nearby devices remain send targets and transfers are events.
    private val _peerGrants = MutableStateFlow<List<PeerGrant>>(emptyList())
    val peerGrants: StateFlow<List<PeerGrant>> = _peerGrants.asStateFlow()

    private val _grantedPeers = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val grantedPeers: StateFlow<List<DeviceProfile>> = _grantedPeers.asStateFlow()

    fun upsert(device: DeviceProfile, sessionToken: String) {
        val existing = _peerGrants.value.firstOrNull { it.identity.deviceId == device.id }
        val route = device.hostAddress?.let { PeerRoute(it, device.port) } ?: existing?.route ?: return
        val grant = PeerGrant(
            identity = PeerIdentity(device.id, device.name, device.type),
            route = route,
            sessionToken = sessionToken
        )
        _peerGrants.value = _peerGrants.value
            .filterNot { it.identity.deviceId == device.id } + grant
        publishDevices()
    }

    fun delete(deviceId: String) {
        _peerGrants.value =
            _peerGrants.value.filterNot { it.identity.deviceId == deviceId }
        publishDevices()
    }

    fun hasValidGrant(deviceId: String, sessionToken: String): Boolean {
        return grantFor(deviceId)?.sessionToken == sessionToken
    }

    fun sessionTokenFor(deviceId: String): String? = grantFor(deviceId)?.sessionToken

    fun routeFor(deviceId: String): PeerRoute? = grantFor(deviceId)?.route

    fun grantFor(deviceId: String): PeerGrant? {
        return _peerGrants.value.firstOrNull { it.identity.deviceId == deviceId }
    }

    fun hasGrantFor(deviceId: String): Boolean = grantFor(deviceId) != null

    @Deprecated("Use hasValidGrant; session wording is kept only for Phase 2 compatibility.")
    fun hasValidSession(deviceId: String, sessionToken: String): Boolean =
        hasValidGrant(deviceId, sessionToken)

    @Deprecated("Use grantFor; session wording is kept only for Phase 2 compatibility.")
    fun sessionFor(deviceId: String): PeerGrant? = grantFor(deviceId)

    private fun publishDevices() {
        _grantedPeers.value = _peerGrants.value.map { grant ->
            DeviceProfile(
                id = grant.identity.deviceId,
                name = grant.identity.name,
                type = grant.identity.type,
                hostAddress = grant.route.host,
                port = grant.route.port,
                isOnline = true
            )
        }
    }
}

internal data class PeerGrant(
    val identity: PeerIdentity,
    val route: PeerRoute,
    val sessionToken: String
)
