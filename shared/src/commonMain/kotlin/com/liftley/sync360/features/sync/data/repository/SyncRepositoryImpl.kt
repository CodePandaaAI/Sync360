package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.network.FilePayload
import com.liftley.sync360.core.network.SyncPayload
import com.liftley.sync360.core.network.SyncPayloadCodec
import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class SyncRepositoryImpl(
    private val networkService: SyncNetworkService,
    private val discoveryService: NetworkDiscoveryService,
    private val localDevice: DeviceProfile,
    private val incomingNotifier: IncomingMessageNotifier,
    private val localLanIp: String,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = DEFAULT_PORT
) : SyncRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    override val activeDeviceId: Flow<String?> = _activeDeviceId.asStateFlow()

    override val connectionStatus: Flow<ConnectionStatus> = networkService.connectionStatus

    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val nearbyDevices: Flow<List<DeviceProfile>> = _nearbyDevices.asStateFlow()

    private val _pendingIncoming = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val pendingIncomingConnectRequests: Flow<List<DeviceProfile>> = _pendingIncoming.asStateFlow()

    private val _pendingOutgoing = MutableStateFlow<DeviceProfile?>(null)
    override val pendingOutgoingConnectDevice: Flow<DeviceProfile?> = _pendingOutgoing.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: Flow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: kotlinx.coroutines.Job? = null

    private val deviceNameById = mutableMapOf<String, String>()

    // --- Ephemeral Volatile RAM Storage (Active Connected Session Only) ---
    private val _pairedDevicesList = MutableStateFlow<List<DeviceProfile>>(emptyList())
    private val _currentMessagesList = MutableStateFlow<List<SyncMessage>>(emptyList())
    private val conversationMessagesMap = mutableMapOf<String, List<SyncMessage>>()

    override val pairedDevices: Flow<List<DeviceProfile>> = combine(
        _pairedDevicesList,
        _nearbyDevices,
        _activeDeviceId,
        connectionStatus
    ) { pairedList, nearby, activeId, status ->
        pairedList.map { entity ->
            val isCurrentlyConnected = entity.id == activeId && status == ConnectionStatus.CONNECTED
            val live = nearby.firstOrNull { it.id == entity.id }
            entity.copy(
                hostAddress = live?.hostAddress ?: entity.hostAddress,
                isOnline = live != null || isCurrentlyConnected
            )
        }
    }

    override val conversationMessages: Flow<List<SyncMessage>> = _currentMessagesList.asStateFlow()

    init {
        scope.launch {
            discoveryService.discoveredDevices.collect { discovered ->
                // Filter out self-devices using multiple robust strategies
                _nearbyDevices.value = discovered.filter { 
                    it.id != localDevice.id && 
                    it.hostAddress != localLanIp && 
                    it.hostAddress != "127.0.0.1" && 
                    it.hostAddress != "localhost"
                }
                discovered.forEach { deviceNameById[it.id] = it.name }
            }
        }

        scope.launch {
            networkService.incomingPayloads.collect { json ->
                handleIncomingPayload(json)
            }
        }

        scope.launch {
            var wasConnected = false
            networkService.connectionStatus.collect { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    wasConnected = true
                } else if (status == ConnectionStatus.DISCONNECTED && wasConnected) {
                    wasConnected = false
                    println("Sudden network connection drop detected. Clearing active device.")
                    _activeDeviceId.value = null
                    _pairedDevicesList.value = emptyList()
                    conversationMessagesMap.clear()
                    _currentMessagesList.value = emptyList()
                }
            }
        }

        // Keep current messages perfectly in sync with activeDeviceId changes
        scope.launch {
            _activeDeviceId.collect { activeId ->
                if (activeId == null) {
                    _currentMessagesList.value = emptyList()
                } else {
                    _currentMessagesList.value = conversationMessagesMap[activeId] ?: emptyList()
                }
            }
        }
    }

    override fun startSync() {
        discoveryService.registerHost(
            port = syncPort,
            deviceId = localDevice.id,
            deviceName = localDevice.name,
            deviceType = localDevice.type.name
        )
        networkService.startServer(syncPort)
        triggerManualScan()
    }

    override fun stopSync() {
        scanJob?.cancel()
        discoveryService.stopDiscovery()
        networkService.stopServer()
        _isScanning.value = false
    }

    override fun triggerManualScan() {
        scanJob?.cancel()
        _isScanning.value = true
        discoveryService.startDiscovery()
        scanJob = scope.launch {
            kotlinx.coroutines.delay(7000)
            discoveryService.stopDiscovery()
            _isScanning.value = false
            println("Autodiscovery: Scan automatically stopped after 7 seconds to save battery.")
        }
    }

    override fun requestConnect(device: DeviceProfile) {
        _pendingOutgoing.value = device
    }

    override fun confirmOutgoingConnect() {
        val device = _pendingOutgoing.value ?: return
        _pendingOutgoing.value = null
        
        // We DO NOT set activeDeviceId or pairedDevicesList here.
        // The screen transition will strictly wait until the other device accepts the request.
        networkService.connectToPeer(device.connectionHost, syncPort, localDevice.id)
        
        scope.launch {
            try {
                // Wait for the client socket to connect successfully (up to 8 seconds)
                withTimeout(8000.milliseconds) {
                    networkService.isClientConnected
                        .first { it }
                }
                
                // Now send the handshake request!
                sendWirePayload(
                    kind = KIND_CONNECT_REQUEST,
                    content = localLanIp,
                    targetDeviceId = device.id,
                    peerDeviceId = device.id
                )
            } catch (e: Exception) {
                println("Client: Connect handshake failed - ${e.message}")
            }
        }
    }

    override fun dismissOutgoingConnect() {
        _pendingOutgoing.value = null
    }

    override fun acceptIncomingConnect(deviceId: String) {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return
        _pendingIncoming.value = _pendingIncoming.value.filter { it.id != deviceId }
        _activeDeviceId.value = device.id
        persistDevice(device)
        sendWirePayload(
            kind = KIND_CONNECT_ACCEPT,
            content = localDevice.name,
            targetDeviceId = device.id,
            peerDeviceId = device.id
        )
    }

    override fun declineIncomingConnect(deviceId: String) {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return
        _pendingIncoming.value = _pendingIncoming.value.filter { it.id != deviceId }
        sendWirePayload(
            kind = KIND_CONNECT_REJECT,
            content = "",
            targetDeviceId = device.id,
            peerDeviceId = device.id
        )
    }

    override fun switchActiveDevice(deviceId: String) {
        _activeDeviceId.value = deviceId
    }

    override fun disconnectActivePeer() {
        val peerId = _activeDeviceId.value
        if (peerId != null) {
            conversationMessagesMap.remove(peerId)
            _pairedDevicesList.value = _pairedDevicesList.value.filter { it.id != peerId }
        }
        _activeDeviceId.value = null
        networkService.disconnectFromPeer()
    }

    override fun disconnectAll() {
        conversationMessagesMap.clear()
        _currentMessagesList.value = emptyList()
        _pairedDevicesList.value = emptyList()
        _activeDeviceId.value = null
        _pendingOutgoing.value = null
        _pendingIncoming.value = emptyList()
        networkService.disconnectFromPeer()
        stopSync()
    }

    override fun sendText(text: String) {
        val peerId = _activeDeviceId.value ?: return
        sendWirePayload(
            kind = KIND_TEXT,
            content = text,
            targetDeviceId = peerId,
            peerDeviceId = peerId,
            persistSent = true
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun sendFile(fileName: String, mimeType: String, content: ByteArray) {
        val peerId = _activeDeviceId.value ?: return
        val encoded = Base64.encode(content)
        val filePayload = FilePayload(
            fileName = fileName,
            mimeType = mimeType,
            fileSize = content.size.toLong(),
            base64Data = encoded
        )
        // Send payload directly and persist locally with original fileName as displayContent
        sendWirePayload(
            kind = KIND_FILE_OFFER,
            content = SyncPayloadCodec.encodeFile(filePayload),
            targetDeviceId = peerId,
            peerDeviceId = peerId,
            persistSent = true,
            mimeType = mimeType,
            displayContent = fileName
        )
    }

    override suspend fun clearAllData() {
        conversationMessagesMap.clear()
        _currentMessagesList.value = emptyList()
        _pairedDevicesList.value = emptyList()
        _activeDeviceId.value = null
        _pendingOutgoing.value = null
        _pendingIncoming.value = emptyList()
        networkService.disconnectFromPeer()
    }

    override fun deleteDevice(deviceId: String) {
        conversationMessagesMap.remove(deviceId)
        _pairedDevicesList.value = _pairedDevicesList.value.filter { it.id != deviceId }
        if (_activeDeviceId.value == deviceId) {
            _activeDeviceId.value = null
        }
    }

    private fun persistDevice(device: DeviceProfile) {
        deviceNameById[device.id] = device.name
        val current = _pairedDevicesList.value
        if (current.none { it.id == device.id }) {
            _pairedDevicesList.value = current + device
        } else {
            _pairedDevicesList.value = current.map { 
                if (it.id == device.id) device else it 
            }
        }
    }

    private fun handleIncomingPayload(json: String) {
        val payload = SyncPayloadCodec.decodeOrNull(json) ?: return
        deviceNameById[payload.originDeviceId] = payload.originDeviceName

        when (payload.kind) {
            KIND_CONNECT_REQUEST -> {
                val device = DeviceProfile(
                    id = payload.originDeviceId,
                    name = payload.originDeviceName,
                    type = parseDeviceType(payload.originDeviceType),
                    hostAddress = payload.content.takeIf { it.contains('.') },
                    isOnline = true
                )
                if (_pendingIncoming.value.none { it.id == device.id }) {
                    _pendingIncoming.value += device
                }
            }
            KIND_CONNECT_ACCEPT -> {
                _pendingOutgoing.value = null
                persistDevice(
                    DeviceProfile(
                        id = payload.originDeviceId,
                        name = payload.originDeviceName,
                        type = parseDeviceType(payload.originDeviceType),
                        isOnline = true
                    )
                )
                _activeDeviceId.value = payload.originDeviceId
            }
            KIND_CONNECT_REJECT -> {
                if (_activeDeviceId.value == payload.originDeviceId) {
                    _activeDeviceId.value = null
                    _pairedDevicesList.value = emptyList()
                    conversationMessagesMap.clear()
                    _currentMessagesList.value = emptyList()
                }
                networkService.disconnectFromPeer()
            }
            KIND_TEXT, "clipboard" -> {
                val peerId = payload.originDeviceId
                if (payload.targetDeviceId != null && payload.targetDeviceId != localDevice.id) return
                persistMessage(
                    itemId = payload.messageId ?: generateUniqueId(),
                    peerDeviceId = peerId,
                    originDeviceId = payload.originDeviceId,
                    kind = KIND_TEXT,
                    mimeType = "text/plain",
                    content = payload.content,
                    timestamp = payload.timestamp
                )
                if (_activeDeviceId.value != peerId) {
                    _activeDeviceId.value = peerId
                    persistDevice(
                        DeviceProfile(
                            id = peerId,
                            name = payload.originDeviceName,
                            type = parseDeviceType(payload.originDeviceType),
                            isOnline = true
                        )
                    )
                }
                incomingNotifier.notifyIncoming(
                    senderName = payload.originDeviceName,
                    preview = payload.content.take(120),
                    isFile = false
                )
            }
            KIND_FILE_OFFER -> {
                val peerId = payload.originDeviceId
                val file = SyncPayloadCodec.decodeFileOrNull(payload.content)
                val label = file?.fileName ?: "File"
                
                @OptIn(ExperimentalEncodingApi::class)
                val bytes = file?.base64Data?.let {
                    try { Base64.decode(it) } catch (_: Exception) { null }
                }

                if (bytes != null) {
                    platformOperations.saveFile(label, bytes) { success, savedPath ->
                        val finalPath = if (success && savedPath != null) savedPath else label
                        persistMessage(
                            itemId = payload.messageId ?: generateUniqueId(),
                            peerDeviceId = peerId,
                            originDeviceId = payload.originDeviceId,
                            kind = KIND_FILE_OFFER,
                            mimeType = label,
                            content = finalPath,
                            timestamp = payload.timestamp
                        )
                    }
                } else {
                    persistMessage(
                        itemId = payload.messageId ?: generateUniqueId(),
                        peerDeviceId = peerId,
                        originDeviceId = payload.originDeviceId,
                        kind = KIND_FILE_OFFER,
                        mimeType = label,
                        content = label,
                        timestamp = payload.timestamp
                    )
                }

                incomingNotifier.notifyIncoming(
                    senderName = payload.originDeviceName,
                    preview = label,
                    isFile = true
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun sendWirePayload(
        kind: String,
        content: String,
        targetDeviceId: String?,
        peerDeviceId: String,
        persistSent: Boolean = false,
        mimeType: String = "text/plain",
        displayContent: String = content
    ) {
        val payload = SyncPayload(
            kind = kind,
            originDeviceId = localDevice.id,
            originDeviceName = localDevice.name,
            originDeviceType = localDevice.type.name,
            content = content,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            targetDeviceId = targetDeviceId,
            messageId = generateUniqueId()
        )
        val json = SyncPayloadCodec.encode(payload)
        networkService.sendToPeer(json)
        networkService.broadcastToClients(json)

        if (persistSent) {
            persistMessage(
                itemId = payload.messageId ?: generateUniqueId(),
                peerDeviceId = peerDeviceId,
                originDeviceId = localDevice.id,
                kind = kind,
                mimeType = mimeType,
                content = displayContent,
                timestamp = payload.timestamp
            )
        }
    }

    private fun persistMessage(
        itemId: String,
        peerDeviceId: String,
        originDeviceId: String,
        kind: String,
        mimeType: String,
        content: String,
        timestamp: Long
    ) {
        val isFile = kind == KIND_FILE_OFFER
        val message = SyncMessage(
            id = itemId,
            peerDeviceId = peerDeviceId,
            text = content,
            isFromMe = originDeviceId == localDevice.id,
            timestamp = timestamp,
            isFile = isFile,
            fileName = if (isFile) mimeType else null
        )

        val currentList = conversationMessagesMap[peerDeviceId] ?: emptyList()
        val updatedList = currentList + message
        conversationMessagesMap[peerDeviceId] = updatedList

        if (_activeDeviceId.value == peerDeviceId) {
            _currentMessagesList.value = updatedList
        }
    }

    private fun generateUniqueId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val randomPart = (1..16).map { chars.random() }.joinToString("")
        val timestamp = Clock.System.now().toEpochMilliseconds()
        return "$timestamp-$randomPart"
    }

    private fun parseDeviceType(value: String): DeviceType =
        runCatching { DeviceType.valueOf(value) }.getOrDefault(DeviceType.DESKTOP)

    companion object {
        const val DEFAULT_PORT = 8080
        const val KIND_CONNECT_REQUEST = "connect_request"
        const val KIND_CONNECT_ACCEPT = "connect_accept"
        const val KIND_CONNECT_REJECT = "connect_reject"
        const val KIND_TEXT = "text"
        const val KIND_FILE_OFFER = "file_offer"
    }
}
