package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.HttpTransportError
import com.liftley.sync360.features.sync.data.network.HttpTransportResult
import com.liftley.sync360.features.sync.domain.model.ConnectionEvent
import com.liftley.sync360.features.sync.domain.model.ConnectionFailure
import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.UserFacingFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class OutgoingConnectionCoordinator(
    private val scope: CoroutineScope,
    private val deviceSession: DeviceSessionStore,
    private val sessionTokens: SessionTokenStore,
    private val sessionAuthenticator: SessionAuthenticator,
    private val httpClient: HttpSyncClient,
    private val events: MutableSharedFlow<ConnectionEvent>
) {
    private var job: Job? = null

    fun confirm() {
        val device = deviceSession.pendingOutgoingValue ?: return
        val host = device.hostAddress
        if (host == null) {
            fail(ConnectionFailure.MISSING_ROUTE, UserFacingFailure.MISSING_ROUTE)
            return
        }

        job?.cancel()
        deviceSession.beginConnecting()
        job = scope.launch {
            val token = sessionAuthenticator.newSessionToken()
            sessionTokens.put(device.id, token)
            val result = withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
                httpClient.sendConnectRequest(
                    host,
                    device.port,
                    sessionAuthenticator.connectRequest(token, host)
                )
            }
            if (result?.isSuccess != true) {
                sessionTokens.remove(device.id)
                fail(result.toFailure(), result.toUserFacingFailure(), host)
                return@launch
            }

            deviceSession.awaitApproval()
            events.emit(ConnectionEvent.WaitingForApproval(device.name))
            delay(APPROVAL_TIMEOUT_MILLIS)
            if (
                deviceSession.stateValue is ConnectionState.AwaitingApproval &&
                deviceSession.pendingOutgoingValue?.id == device.id
            ) {
                sessionTokens.remove(device.id)
                fail(
                    ConnectionFailure.APPROVAL_TIMEOUT,
                    UserFacingFailure.APPROVAL_TIMEOUT,
                    device.name
                )
            }
        }
    }

    fun dismiss() {
        cancel()
        deviceSession.clearOutgoing()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun fail(
        reason: ConnectionFailure,
        userFacingFailure: UserFacingFailure,
        peer: String? = null
    ) {
        deviceSession.failConnecting(reason)
        events.tryEmit(ConnectionEvent.Failed(userFacingFailure, peer))
    }

    private fun HttpTransportResult?.toFailure(): ConnectionFailure {
        return when ((this as? HttpTransportResult.Failure)?.error) {
            HttpTransportError.TIMEOUT -> ConnectionFailure.REQUEST_TIMEOUT
            HttpTransportError.UNREACHABLE -> ConnectionFailure.UNREACHABLE
            HttpTransportError.BUSY -> ConnectionFailure.PEER_BUSY
            HttpTransportError.PROTOCOL_MISMATCH -> ConnectionFailure.PROTOCOL_MISMATCH
            HttpTransportError.CLIENT_CLOSED -> ConnectionFailure.CLIENT_CLOSED
            HttpTransportError.REJECTED -> ConnectionFailure.SERVER_UNAVAILABLE
            null -> ConnectionFailure.REQUEST_TIMEOUT
            else -> ConnectionFailure.UNKNOWN
        }
    }

    private fun HttpTransportResult?.toUserFacingFailure(): UserFacingFailure {
        return when ((this as? HttpTransportResult.Failure)?.error) {
            HttpTransportError.TIMEOUT -> UserFacingFailure.REQUEST_TIMEOUT
            HttpTransportError.UNREACHABLE -> UserFacingFailure.UNREACHABLE
            HttpTransportError.BUSY -> UserFacingFailure.PEER_BUSY
            HttpTransportError.PROTOCOL_MISMATCH -> UserFacingFailure.PROTOCOL_MISMATCH
            HttpTransportError.CLIENT_CLOSED -> UserFacingFailure.CLIENT_CLOSED
            HttpTransportError.REJECTED -> UserFacingFailure.SERVER_UNAVAILABLE
            else -> UserFacingFailure.UNKNOWN
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
        const val APPROVAL_TIMEOUT_MILLIS = 30_000L
    }
}
