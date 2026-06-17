package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.data.network.ConnectRequestOutcome
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.api.ConnectAcceptDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRejectDto
import com.liftley.sync360.features.sync.data.network.api.ConnectRequestDto
import com.liftley.sync360.features.sync.data.network.api.SyncProtocol
import com.liftley.sync360.features.sync.domain.model.ConnectionEvent
import com.liftley.sync360.features.sync.domain.model.ConnectionFailure
import com.liftley.sync360.features.sync.domain.model.ConnectionSnapshot
import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.PeerRoute
import com.liftley.sync360.features.sync.domain.model.SessionSecurityMode
import com.liftley.sync360.features.sync.domain.model.SessionSnapshot
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.model.UserFacingFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class ConnectionEngine(
    private val scope: CoroutineScope,
    private val localDevice: DeviceProfile,
    private val syncPort: Int,
    private val httpClient: HttpSyncClient,
    private val deviceSession: DeviceSessionStore,
    private val deviceRegistry: DeviceRegistry,
    private val sessionTokens: SessionTokenStore,
    private val sessionAuthenticator: SessionAuthenticator,
    private val events: MutableSharedFlow<ConnectionEvent>,
    private val hasActiveTransfer: () -> Boolean,
    private val onSessionCleared: () -> Unit,
    private val onDeviceDeleted: (String) -> Unit
) {
    private val outgoing = OutgoingConnectionCoordinator(
        scope = scope,
        deviceSession = deviceSession,
        sessionTokens = sessionTokens,
        sessionAuthenticator = sessionAuthenticator,
        httpClient = httpClient,
        events = events
    )

    val connectionSnapshot: Flow<ConnectionSnapshot> = deviceSession.snapshot
    val sessionSnapshot: Flow<SessionSnapshot> = combine(
        deviceRegistry.peerGrants,
        deviceSession.snapshot
    ) { grants, connection ->
        val activeId = when (val state = connection.state) {
            is ConnectionState.Connected -> state.deviceId
            is ConnectionState.Disconnecting -> state.deviceId
            else -> null
        }
        val activeGrant = grants.firstOrNull { it.identity.deviceId == activeId }
        if (activeGrant == null) {
            SessionSnapshot.NoSession
        } else {
            SessionSnapshot.Approved(
                identity = activeGrant.identity,
                route = activeGrant.route,
                securityMode = SessionSecurityMode.TRUSTED_LAN_PLAINTEXT
            )
        }
    }

    fun request(device: DeviceProfile) {
        deviceSession.requestOutgoing(device)
    }

    fun requestByHost(hostAddress: String) {
        val endpoint = parseManualEndpoint(hostAddress)
        if (endpoint == null) {
            deviceSession.fail(ConnectionFailure.INVALID_HOST)
            events.tryEmit(ConnectionEvent.Failed(UserFacingFailure.INVALID_HOST))
            return
        }
        deviceSession.requestOutgoing(
            DeviceProfile(
                id = "$MANUAL_DEVICE_ID_PREFIX${endpoint.host}:${endpoint.port}",
                name = endpoint.displayName,
                type = DeviceType.DESKTOP,
                hostAddress = endpoint.host,
                port = endpoint.port,
                isOnline = true
            )
        )
    }

    fun confirm() = outgoing.confirm()

    fun dismiss() = outgoing.dismiss()

    fun cancelPending() = outgoing.cancel()

    fun acceptIncoming(deviceId: String) {
        val device = deviceSession.removeIncoming(deviceId) ?: return
        val sessionToken = sessionTokens.remove(device.id) ?: sessionAuthenticator.newSessionToken()
        deviceRegistry.upsert(device, sessionToken)
        deviceSession.connect(device.id)
        events.tryEmit(ConnectionEvent.Connected(device.name))

        device.hostAddress?.let { host ->
            scope.launch {
                httpClient.sendConnectAccept(
                    host,
                    device.port,
                    sessionAuthenticator.connectAccept(sessionToken, host)
                )
            }
        }
    }

    fun declineIncoming(deviceId: String) {
        val device = deviceSession.removeIncoming(deviceId) ?: return
        val sessionToken = sessionTokens.remove(device.id)
        device.hostAddress?.let { host ->
            scope.launch {
                httpClient.sendConnectReject(
                    host,
                    device.port,
                    sessionAuthenticator.connectReject(sessionToken)
                )
            }
        }
    }



    fun clearSession() {
        outgoing.cancel()
        deviceSession.disconnect()
        sessionTokens.clear()
        sessionAuthenticator.clearReplayHistory()
        onSessionCleared()
    }

    fun activePeerRoute(): PeerRoute? {
        val activeId = deviceSession.activeDeviceIdValue ?: return null
        return deviceRegistry.routeFor(activeId)
    }

    fun activePeerId(): String? = deviceSession.activeDeviceIdValue

    fun hasPeerGrantAtRoute(
        deviceId: String,
        sessionToken: String?,
        remoteHost: String
    ): Boolean {
        if (sessionToken == null) return false
        val grant = deviceRegistry.grantFor(deviceId) ?: return false
        return grant.sessionToken == sessionToken && grant.route.host == remoteHost
    }

    fun hasPeerGrantTokenAtRoute(sessionToken: String, remoteHost: String): Boolean {
        return deviceRegistry.peerGrants.value.any {
            it.sessionToken == sessionToken && it.route.host == remoteHost
        }
    }

    fun onConnectRequest(
        request: ConnectRequestDto,
        remoteHost: String
    ): ConnectRequestOutcome {
        if (request.deviceId == localDevice.id) return ConnectRequestOutcome.FORBIDDEN
        if (!SyncProtocol.isCompatible(request.protocolVersion, request.capabilities)) {
            return ConnectRequestOutcome.PROTOCOL_MISMATCH
        }
        if (!request.hasValidConnectFields()) return ConnectRequestOutcome.FORBIDDEN
        if (!sessionAuthenticator.verifyConnectRequest(request)) {
            return ConnectRequestOutcome.FORBIDDEN
        }

        val device = DeviceProfile(
            id = request.deviceId,
            name = request.deviceName,
            type = parseDeviceType(request.deviceType),
            hostAddress = remoteHost,
            port = request.senderPort,
            isOnline = true
        )
        val activePeerId = deviceSession.activeDeviceIdValue
        if (
            hasActiveTransfer() ||
            deviceSession.pendingIncomingValue.size >= SyncProtocolLimits.MAX_PENDING_CONNECT_REQUESTS ||
            (activePeerId != null && activePeerId != device.id)
        ) {
            return ConnectRequestOutcome.BUSY
        }
        deviceRegistry.upsert(device, request.sessionToken)
        deviceSession.connect(device.id)
        events.tryEmit(ConnectionEvent.Connected(device.name))
        scope.launch {
            httpClient.sendConnectAccept(
                remoteHost,
                device.port,
                sessionAuthenticator.connectAccept(request.sessionToken, remoteHost)
            )
        }
        return ConnectRequestOutcome.RECEIVED
    }

    fun onConnectAccept(accept: ConnectAcceptDto, remoteHost: String): Boolean {
        if (accept.deviceId == localDevice.id) return false
        if (!accept.hasValidConnectFields()) return false

        val pending = deviceSession.pendingOutgoingValue
        val pendingToken = sessionTokens.get(accept.deviceId) ?: pending?.id?.let(sessionTokens::get)
        val alreadyGranted = hasPeerGrantAtRoute(
            accept.deviceId,
            accept.sessionToken,
            remoteHost
        )
        val matchesPendingDevice = pending?.let {
            val hostMatches = it.hostAddress == remoteHost || it.id.startsWith(MANUAL_DEVICE_ID_PREFIX)
            hostMatches &&
                it.port == accept.senderPort &&
                (it.id == accept.deviceId || it.id.startsWith(MANUAL_DEVICE_ID_PREFIX))
        } == true
        val acceptsPendingRequest = matchesPendingDevice && pendingToken == accept.sessionToken
        if (!acceptsPendingRequest && !alreadyGranted) return false
        if (!sessionAuthenticator.verifyConnectAccept(accept)) return false
        if (!SyncProtocol.isCompatible(accept.protocolVersion, accept.capabilities)) {
            outgoing.cancel()
            sessionTokens.clear()
            deviceSession.failConnecting(ConnectionFailure.PROTOCOL_MISMATCH)
            events.tryEmit(
                ConnectionEvent.Failed(UserFacingFailure.PROTOCOL_MISMATCH, accept.deviceName)
            )
            return false
        }

        outgoing.cancel()
        deviceSession.clearOutgoing()
        sessionTokens.remove(accept.deviceId)
        pending?.id?.let(sessionTokens::remove)
        deviceRegistry.upsert(
            DeviceProfile(
                id = accept.deviceId,
                name = accept.deviceName,
                type = parseDeviceType(accept.deviceType),
                hostAddress = remoteHost,
                port = accept.senderPort,
                isOnline = true
            ),
            accept.sessionToken
        )
        deviceSession.connect(accept.deviceId)
        events.tryEmit(ConnectionEvent.Connected(accept.deviceName))
        return true
    }

    fun onConnectReject(reject: ConnectRejectDto, remoteHost: String): Boolean {
        if (!sessionAuthenticator.verifyConnectReject(reject)) return false

        val pending = deviceSession.pendingOutgoingValue
        val pendingId = pending?.id
        val activeId = deviceSession.activeDeviceIdValue
        val tokenMatchesPending = sessionTokens.containsToken(reject.sessionToken)
        val pendingRouteMatches = pending?.let {
            it.hostAddress == remoteHost || it.id.startsWith(MANUAL_DEVICE_ID_PREFIX)
        } == true
        val allowed = hasPeerGrantAtRoute(
            reject.senderDeviceId,
            reject.sessionToken,
            remoteHost
        ) ||
            (pendingId == reject.senderDeviceId &&
                pendingRouteMatches &&
                sessionTokens.get(reject.senderDeviceId) == reject.sessionToken) ||
            (tokenMatchesPending && pendingRouteMatches)
        if (!allowed) return false

        if (pendingId == reject.senderDeviceId || tokenMatchesPending) {
            outgoing.cancel()
            deviceSession.failConnecting(ConnectionFailure.DECLINED)
            sessionTokens.removeToken(reject.sessionToken)
            events.tryEmit(ConnectionEvent.Failed(UserFacingFailure.DECLINED))
        }
        if (activeId == reject.senderDeviceId) {
            if (hasActiveTransfer()) return false
            clearSession()
        }
        return true
    }

    private fun parseManualEndpoint(value: String): ManualEndpoint? {
        val authority = value
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .trim()
        if (authority.isBlank()) return null

        val colonCount = authority.count { it == ':' }
        if (colonCount > 1) return null
        val host = authority.substringBefore(':').trim()
        val port = if (colonCount == 1) {
            authority.substringAfter(':').toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: return null
        } else {
            syncPort
        }
        if (host.isBlank() || host.length > SyncProtocolLimits.MAX_HOST_LENGTH) return null
        if (host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null

        val ipv4Parts = host.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            if (ipv4Parts.any { part ->
                    val number = part.toIntOrNull()
                    number == null || number !in 0..255
                }
            ) return null
        }
        return ManualEndpoint(host, port)
    }

    private fun parseDeviceType(value: String): DeviceType =
        runCatching { DeviceType.valueOf(value) }.getOrDefault(DeviceType.DESKTOP)

    private data class ManualEndpoint(val host: String, val port: Int) {
        val displayName: String
            get() = "$host:$port"
    }

    private companion object {
        const val MAX_PORT = 65_535
        const val MANUAL_DEVICE_ID_PREFIX = "manual:"
    }
}

private fun ConnectRequestDto.hasValidConnectFields(): Boolean {
    return deviceId.isNotBlank() &&
        deviceId.length <= SyncProtocolLimits.MAX_DEVICE_ID_LENGTH &&
        deviceName.isNotBlank() &&
        deviceName.length <= SyncProtocolLimits.MAX_DEVICE_NAME_LENGTH &&
        deviceType.isNotBlank() &&
        deviceType.length <= SyncProtocolLimits.MAX_DEVICE_TYPE_LENGTH &&
        senderIp.isNotBlank() &&
        senderIp.length <= SyncProtocolLimits.MAX_HOST_LENGTH &&
        senderPort in 1..65_535 &&
        sessionToken.isConnectionProtocolHex(SyncProtocolLimits.SESSION_TOKEN_HEX_LENGTH) &&
        nonce.isConnectionProtocolHex(SyncProtocolLimits.NONCE_HEX_LENGTH) &&
        signature.isConnectionProtocolHex(SyncProtocolLimits.SIGNATURE_HEX_LENGTH) &&
        capabilities.size <= 16 &&
        capabilities.all { it.length <= 64 }
}

private fun ConnectAcceptDto.hasValidConnectFields(): Boolean {
    return deviceId.isNotBlank() &&
        deviceId.length <= SyncProtocolLimits.MAX_DEVICE_ID_LENGTH &&
        deviceName.isNotBlank() &&
        deviceName.length <= SyncProtocolLimits.MAX_DEVICE_NAME_LENGTH &&
        deviceType.isNotBlank() &&
        deviceType.length <= SyncProtocolLimits.MAX_DEVICE_TYPE_LENGTH &&
        senderIp.isNotBlank() &&
        senderIp.length <= SyncProtocolLimits.MAX_HOST_LENGTH &&
        senderPort in 1..65_535 &&
        sessionToken.isConnectionProtocolHex(SyncProtocolLimits.SESSION_TOKEN_HEX_LENGTH) &&
        nonce.isConnectionProtocolHex(SyncProtocolLimits.NONCE_HEX_LENGTH) &&
        signature.isConnectionProtocolHex(SyncProtocolLimits.SIGNATURE_HEX_LENGTH) &&
        capabilities.size <= 16 &&
        capabilities.all { it.length <= 64 }
}

private fun String.isConnectionProtocolHex(expectedLength: Int): Boolean {
    return length == expectedLength && all { it in "0123456789abcdefABCDEF" }
}
