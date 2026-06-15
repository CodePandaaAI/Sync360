package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.features.sync.data.network.HttpSyncClient
import com.liftley.sync360.features.sync.data.network.api.MessageDto
import com.liftley.sync360.features.sync.domain.model.ConnectionEvent
import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.model.UserFacingFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal class MessageEngine(
    private val scope: CoroutineScope,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val httpClient: HttpSyncClient,
    private val deviceSession: DeviceSessionStore,
    private val deviceRegistry: DeviceRegistry,
    private val sessionAuthenticator: SessionAuthenticator,
    private val events: MutableSharedFlow<ConnectionEvent>
) {
    private val textStore = SessionTextStore()
    private val _messages = MutableStateFlow<List<SyncMessage>>(emptyList())
    val messages: Flow<List<SyncMessage>> = _messages.asStateFlow()

    init {
        scope.launch {
            deviceSession.snapshot
                .map { snapshot ->
                    when (val state = snapshot.state) {
                        is ConnectionState.Connected -> state.deviceId
                        is ConnectionState.Disconnecting -> state.deviceId
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { activeId ->
                    _messages.value = textStore.messagesFor(activeId)
                }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || text.length > SyncProtocolLimits.MAX_TEXT_LENGTH) {
            events.tryEmit(ConnectionEvent.Failed(UserFacingFailure.TEXT_INVALID))
            return
        }
        val peerId = deviceSession.activeDeviceIdValue ?: return
        val route = deviceRegistry.routeFor(peerId) ?: return
        val sessionToken = deviceRegistry.sessionTokenFor(peerId) ?: return
        val message = MessageDto(
            messageId = newSyncItemId(),
            senderDeviceId = localDevice.id,
            senderName = localDevice.name,
            content = text,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            sessionToken = sessionToken,
            issuedAtMillis = 0L,
            nonce = "",
            signature = ""
        )
        val signed = sessionAuthenticator.signTextMessage(message)

        scope.launch {
            if (httpClient.sendTextMessage(route.host, route.port, signed).isSuccess) {
                append(signed, peerId)
            } else {
                events.emit(ConnectionEvent.Failed(UserFacingFailure.TEXT_DELIVERY_FAILED))
            }
        }
    }

    fun receive(message: MessageDto, remoteHost: String): Boolean {
        if (
            message.messageId.isBlank() ||
            message.messageId.length > SyncProtocolLimits.MAX_OFFER_ID_LENGTH ||
            message.senderDeviceId.length > SyncProtocolLimits.MAX_DEVICE_ID_LENGTH ||
            message.senderName.length > SyncProtocolLimits.MAX_DEVICE_NAME_LENGTH ||
            message.content.isBlank() ||
            message.content.length > SyncProtocolLimits.MAX_TEXT_LENGTH ||
            message.sessionToken.length > SyncProtocolLimits.MAX_SESSION_TOKEN_LENGTH ||
            message.nonce.length > SyncProtocolLimits.MAX_NONCE_LENGTH ||
            message.signature.length > SyncProtocolLimits.MAX_SIGNATURE_LENGTH
        ) return false
        val session = deviceRegistry.sessionFor(message.senderDeviceId) ?: return false
        if (session.sessionToken != message.sessionToken || session.route.host != remoteHost) {
            return false
        }
        if (!sessionAuthenticator.verifyTextMessage(message)) return false

        append(message, message.senderDeviceId)
        incomingNotifier.notifyIncoming(message.senderName, message.content.take(120), false)
        return true
    }

    fun clearVisible() {
        _messages.value = emptyList()
    }

    fun removePeer(peerId: String) {
        textStore.removePeer(peerId)
    }

    private fun append(message: MessageDto, peerId: String) {
        val updated = textStore.addText(
            itemId = message.messageId,
            peerId = peerId,
            originDeviceId = message.senderDeviceId,
            localDeviceId = localDevice.id,
            text = message.content,
            timestamp = message.timestamp
        )
        if (deviceSession.activeDeviceIdValue == peerId) {
            _messages.value = updated
        }
    }
}
