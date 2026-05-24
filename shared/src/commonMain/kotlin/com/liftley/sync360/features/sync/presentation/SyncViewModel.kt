package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.liftley.sync360.core.database.DeviceEntity
import com.liftley.sync360.core.database.SharedItemEntity
import com.liftley.sync360.core.database.SyncDatabase
import com.liftley.sync360.core.network.SyncClient
import com.liftley.sync360.core.network.SyncPayload
import com.liftley.sync360.core.network.SyncPayloadCodec
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.SyncAsset
import com.liftley.sync360.features.sync.domain.model.SyncAssetType
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.model.SyncTransferState
import com.liftley.sync360.features.sync.domain.model.createLocalDeviceProfile
import com.liftley.sync360.features.sync.domain.network.createNetworkDiscoveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SyncViewModel(
    val isDesktop: Boolean,
    val database: SyncDatabase,
    val platformContext: Any? = null,
    val initialServerIp: String = "127.0.0.1",
    val initialServerClientCount: Int = 0,
    private val serverIncomingFlow: Flow<String>? = null,
    private val onServerBroadcast: ((String) -> Unit)? = null,
    private val onStartService: ((String) -> Unit)? = null,
    private val onStopService: (() -> Unit)? = null,
    private val onShowOverlay: (() -> Unit)? = null,
    private val onHideOverlay: (() -> Unit)? = null,
    private val onReadClipboard: (() -> String?)? = null,
    private val onWriteClipboard: ((String) -> Unit)? = null
) : ViewModel() {

    private val discoveryService = createNetworkDiscoveryService(platformContext)
    private val syncClient = if (!isDesktop) SyncClient() else null
    private var pairRequestDevice: DeviceProfile? = null
    private val localDevice = createLocalDeviceProfile(platformContext, isDesktop, initialServerIp)

    private var lastSentText: String? = null
    private var lastReceivedText: String? = null

    private val _uiState = MutableStateFlow(
        SyncUiState(
            isDesktop = isDesktop,
            serverIp = initialServerIp,
            clientCount = initialServerClientCount,
            connectionStatus = if (isDesktop) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        observeDatabase()
        observeNetwork()
        observeDiscovery()

        // Register and Discover on BOTH platforms for symmetric peer presence
        discoveryService.registerHost(8080, localDevice.name, localDevice.type.name)
        discoveryService.startDiscovery()

        // Automatically poll system clipboard on Desktop to sync copies to Mobile
        if (isDesktop && onReadClipboard != null) {
            viewModelScope.launch(Dispatchers.IO) {
                var lastLocalClip: String? = null
                while (isActive) {
                    val clip = onReadClipboard.invoke()
                    if (clip != null && clip != lastLocalClip && clip.isNotBlank()) {
                        lastLocalClip = clip
                        if (_uiState.value.activeDeviceId != null) {
                            sendClipboard(clip)
                        }
                    }
                    delay(1500)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryService.stopDiscovery()
    }

    fun onEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.OnIpChange -> _uiState.update { it.copy(clientIpInput = event.ip) }
            is SyncEvent.Connect -> connectToHost(_uiState.value.clientIpInput)
            is SyncEvent.Disconnect -> disconnect()
            is SyncEvent.SendMessage -> sendClipboard(event.text)
            is SyncEvent.SendCurrentClipboard -> {
                onReadClipboard?.invoke()?.let { sendClipboard(it) }
            }
            is SyncEvent.ReceiveMessage -> receiveFrame(event.text, event.isFromMe)
            is SyncEvent.SwitchDevice -> switchDevice(event.deviceId)
            is SyncEvent.PairWithDevice -> pairWithDevice(event.deviceId)
            is SyncEvent.AcceptPairing -> acceptPairing(event.deviceId)
            is SyncEvent.DeclinePairing -> declinePairing(event.deviceId)
            is SyncEvent.CopyClipboard -> {
                val text = _uiState.value.deviceStreams[event.deviceId]?.clipboard?.text.orEmpty()
                if (text.isNotBlank()) {
                    onWriteClipboard?.invoke(text)
                    addMessage(text, isFromMe = true)
                }
            }
            is SyncEvent.RequestDownload -> markAssetDownloading(event.assetId)
            is SyncEvent.SetOverlayEnabled -> {
                _uiState.update { it.copy(overlayEnabled = event.enabled) }
                if (event.enabled) onShowOverlay?.invoke() else onHideOverlay?.invoke()
            }
            is SyncEvent.SetBackgroundMonitoringEnabled -> {
                _uiState.update { it.copy(backgroundMonitoringEnabled = event.enabled) }
            }
            is SyncEvent.DismissProactivePrompt -> {
                _uiState.update { it.copy(proactivePromptDevice = null) }
            }
            is SyncEvent.ConfirmProactiveConnect -> {
                _uiState.update { it.copy(proactivePromptDevice = null) }
                pairWithDevice(event.device.id)
            }
        }
    }

    private fun observeDatabase() {
        val queries = database.syncDatabaseQueries
        val devicesFlow = queries.selectAllDevices().asFlow().mapToList(Dispatchers.IO)
        val itemsFlow = queries.selectAllItems().asFlow().mapToList(Dispatchers.IO)

        viewModelScope.launch {
            combine(devicesFlow, itemsFlow) { devices, items ->
                devices to items
            }.collect { (devices, items) ->
                // Filter out local device itself to prevent self-connection and naming issues
                val profiles = devices.map { it.toDeviceProfile() }.filter { it.id != localDevice.id }
                val streams = buildStreams(devices.filter { it.deviceId != localDevice.id }, items)
                _uiState.update { state ->
                    val activeId = when {
                        state.activeDeviceId != null && profiles.any { it.id == state.activeDeviceId } -> state.activeDeviceId
                        profiles.isNotEmpty() -> profiles.first().id
                        else -> null
                    }
                    state.copy(
                        connectedDevices = profiles,
                        activeDeviceId = activeId,
                        deviceStreams = streams
                    )
                }
            }
        }
    }

    private fun observeNetwork() {
        if (isDesktop && serverIncomingFlow != null) {
            viewModelScope.launch {
                serverIncomingFlow.collect { frame ->
                    receiveFrame(frame, isFromMe = false)
                }
            }
        }

        if (!isDesktop && syncClient != null) {
            viewModelScope.launch {
                syncClient.incomingMessages.collect { frame ->
                    receiveFrame(frame, isFromMe = false)
                }
            }

            viewModelScope.launch {
                syncClient.connectionStatus.collect { status ->
                    _uiState.update { it.copy(connectionStatus = status) }
                    if (status == ConnectionStatus.CONNECTED) {
                        sendPendingPairRequest()
                    }
                }
            }
        }
    }

    private fun observeDiscovery() {
        viewModelScope.launch {
            discoveryService.discoveredDevices.collect { devices ->
                val filteredNearby = devices.filter { it.id != localDevice.id }
                _uiState.update { it.copy(nearbyDevices = filteredNearby) }

                // Automatically establish WebSocket link in background when nearby hosts found (Mobile)
                if (!isDesktop) {
                    filteredNearby.firstOrNull()?.let { device ->
                        if (_uiState.value.connectionStatus == ConnectionStatus.DISCONNECTED) {
                            connectToHost(device.id)
                        }

                        // Trigger proactive pairing dialog if found device is not already paired/active
                        val isAlreadyPaired = _uiState.value.connectedDevices.any { it.id == device.id }
                        if (!isAlreadyPaired && _uiState.value.proactivePromptDevice?.id != device.id && _uiState.value.activeDeviceId != device.id) {
                            _uiState.update { it.copy(proactivePromptDevice = device) }
                        }
                    }
                } else {
                    // Trigger proactive pairing dialog on Desktop if an unpaired device resolves on the network
                    filteredNearby.firstOrNull { device ->
                        val isPaired = _uiState.value.connectedDevices.any { it.id == device.id }
                        !isPaired
                    }?.let { device ->
                        if (_uiState.value.proactivePromptDevice?.id != device.id && _uiState.value.activeDeviceId != device.id) {
                            _uiState.update { it.copy(proactivePromptDevice = device) }
                        }
                    }
                }
            }
        }
    }

    private fun connectToHost(host: String) {
        if (host.isBlank()) return
        syncClient?.connect(host)
        onStartService?.invoke(host)
    }

    private fun disconnect() {
        syncClient?.disconnect()
        onHideOverlay?.invoke()
        onStopService?.invoke()
    }

    private fun switchDevice(deviceId: String) {
        _uiState.update { it.copy(activeDeviceId = deviceId) }
        if (!isDesktop && deviceId.isNotBlank() && _uiState.value.connectedDevices.any { it.id == deviceId }) {
            connectToHost(deviceId)
        }
    }

    private fun pairWithDevice(deviceId: String) {
        val device = (_uiState.value.nearbyDevices + _uiState.value.connectedDevices)
            .firstOrNull { it.id == deviceId }
            ?: return
        _uiState.update {
            it.copy(
                activeDeviceId = device.id,
                clientIpInput = device.id
            )
        }
        if (isDesktop) {
            upsertDevice(device)
            sendPairingResponse(kind = "pair_accept", targetDeviceId = device.id)
        } else {
            pairRequestDevice = device
            connectToHost(device.id)
        }
    }

    private fun sendClipboard(text: String) {
        if (text.isBlank()) return
        if (text == lastReceivedText) return // Deduplicate loopback
        lastSentText = text

        val payload = SyncPayload(
            kind = "clipboard",
            originDeviceId = localDevice.id,
            originDeviceName = localDevice.name,
            originDeviceType = localDevice.type.name,
            content = text,
            timestamp = nowMillis(),
            targetDeviceId = _uiState.value.activeDeviceId
        )
        val frame = SyncPayloadCodec.encode(payload)

        if (isDesktop) {
            onServerBroadcast?.invoke(frame)
        } else {
            syncClient?.sendFrame(frame)
        }

        persistPayload(payload)
        addMessage(text, isFromMe = true)
    }

    private fun receiveFrame(frame: String, isFromMe: Boolean) {
        val payload = SyncPayloadCodec.decodeOrNull(frame) ?: legacyPayload(frame)
        if (!isPayloadForThisDevice(payload)) return

        when (payload.kind) {
            "pair_request" -> handlePairRequest(payload)
            "pair_accept" -> handlePairAccept(payload)
            "pair_decline" -> handlePairDecline(payload)
            "clipboard" -> {
                // Focused Sync Constraint: only process clipboard if from the currently selected active device
                if (payload.originDeviceId == _uiState.value.activeDeviceId) {
                    if (payload.content == lastSentText) return // Deduplicate loopback
                    lastReceivedText = payload.content

                    persistPayload(payload)
                    addMessage(payload.content, isFromMe = isFromMe)
                    
                    // Write to local system clipboard automatically
                    onWriteClipboard?.invoke(payload.content)
                }
            }
            else -> Unit
        }
    }

    private fun sendPendingPairRequest() {
        val device = pairRequestDevice ?: return
        val payload = SyncPayload(
            kind = "pair_request",
            originDeviceId = localDevice.id,
            originDeviceName = localDevice.name,
            originDeviceType = localDevice.type.name,
            content = "pair",
            timestamp = nowMillis(),
            targetDeviceId = device.id
        )
        syncClient?.sendFrame(SyncPayloadCodec.encode(payload))
        pairRequestDevice = null
    }

    private fun handlePairRequest(payload: SyncPayload) {
        if (!isDesktop) return
        val device = payload.toDeviceProfile()
        _uiState.update { state ->
            val pending = (state.pendingPairingRequests + device).distinctBy { it.id }
            state.copy(pendingPairingRequests = pending)
        }
    }

    private fun acceptPairing(deviceId: String) {
        val device = _uiState.value.pendingPairingRequests.firstOrNull { it.id == deviceId }
            ?: _uiState.value.nearbyDevices.firstOrNull { it.id == deviceId }
            ?: return
        upsertDevice(device)
        _uiState.update { state ->
            state.copy(
                activeDeviceId = device.id,
                pendingPairingRequests = state.pendingPairingRequests.filterNot { it.id == deviceId }
            )
        }
        sendPairingResponse(kind = "pair_accept", targetDeviceId = device.id)
    }

    private fun declinePairing(deviceId: String) {
        _uiState.update { state ->
            state.copy(pendingPairingRequests = state.pendingPairingRequests.filterNot { it.id == deviceId })
        }
        sendPairingResponse(kind = "pair_decline", targetDeviceId = deviceId)
    }

    private fun sendPairingResponse(kind: String, targetDeviceId: String) {
        val payload = SyncPayload(
            kind = kind,
            originDeviceId = localDevice.id,
            originDeviceName = localDevice.name,
            originDeviceType = localDevice.type.name,
            content = kind,
            timestamp = nowMillis(),
            targetDeviceId = targetDeviceId
        )
        if (isDesktop) {
            onServerBroadcast?.invoke(SyncPayloadCodec.encode(payload))
        } else {
            syncClient?.sendFrame(SyncPayloadCodec.encode(payload))
        }
    }

    private fun handlePairAccept(payload: SyncPayload) {
        if (isDesktop) return
        val device = payload.toDeviceProfile()
        upsertDevice(device)
        _uiState.update {
            it.copy(
                activeDeviceId = device.id,
                clientIpInput = device.id
            )
        }
    }

    private fun handlePairDecline(payload: SyncPayload) {
        if (isDesktop) return
        if (payload.targetDeviceId == localDevice.id) {
            disconnect()
        }
    }

    private fun persistPayload(payload: SyncPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            val deviceType = payload.originDeviceType.ifBlank {
                if (isDesktop) DeviceType.PHONE.name else DeviceType.DESKTOP.name
            }
            database.syncDatabaseQueries.insertOrUpdateDevice(
                deviceId = payload.originDeviceId,
                deviceName = payload.originDeviceName,
                deviceType = deviceType,
                lastActiveTimestamp = payload.timestamp
            )
            database.syncDatabaseQueries.insertOrUpdateItem(
                itemId = "clip-${payload.originDeviceId}-${payload.timestamp}-${payload.content.hashCode().absoluteValue}",
                originDeviceId = payload.originDeviceId,
                categoryType = "TEXT",
                mimeType = "text/plain",
                metaContent = payload.content,
                thumbnailBytes = null,
                syncState = SyncTransferState.FULLY_DOWNLOADED.name,
                timestamp = payload.timestamp
            )
        }
    }

    private fun upsertDevice(device: DeviceProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            database.syncDatabaseQueries.insertOrUpdateDevice(
                deviceId = device.id,
                deviceName = device.name,
                deviceType = device.type.name,
                lastActiveTimestamp = nowMillis()
            )
        }
    }

    private fun isPayloadForThisDevice(payload: SyncPayload): Boolean {
        val target = payload.targetDeviceId
        return target == null || target == localDevice.id
    }

    private fun isPairedOrLocal(deviceId: String): Boolean {
        return deviceId == localDevice.id || _uiState.value.connectedDevices.any { it.id == deviceId }
    }

    private fun SyncPayload.toDeviceProfile(): DeviceProfile {
        return DeviceProfile(
            id = originDeviceId,
            name = originDeviceName,
            type = runCatching { DeviceType.valueOf(originDeviceType) }.getOrDefault(DeviceType.DESKTOP)
        )
    }

    private fun addMessage(text: String, isFromMe: Boolean) {
        val newMessage = SyncMessage(text = text, isFromMe = isFromMe, timestamp = nowMillis())
        _uiState.update { state ->
            state.copy(messages = listOf(newMessage) + state.messages.take(49))
        }
    }

    private fun markAssetDownloading(assetId: String) {
        _uiState.update { state ->
            state.copy(
                deviceStreams = state.deviceStreams.mapValues { (_, stream) ->
                    stream.copy(
                        media = stream.media.map { asset ->
                            if (asset.id == assetId) asset.copy(syncState = SyncTransferState.DOWNLOADING) else asset
                        },
                        documents = stream.documents.map { asset ->
                            if (asset.id == assetId) asset.copy(syncState = SyncTransferState.DOWNLOADING) else asset
                        }
                    )
                }
            )
        }
    }

    private fun legacyPayload(frame: String): SyncPayload {
        val activeDevice = _uiState.value.activeDeviceId
            ?.let { id -> _uiState.value.connectedDevices.firstOrNull { it.id == id } }
        val fallbackType = if (isDesktop) DeviceType.PHONE else DeviceType.DESKTOP
        return SyncPayload(
            kind = "clipboard",
            originDeviceId = activeDevice?.id ?: "legacy-peer",
            originDeviceName = activeDevice?.name ?: "Legacy peer",
            originDeviceType = (activeDevice?.type ?: fallbackType).name,
            content = frame,
            timestamp = nowMillis()
        )
    }

    private fun buildStreams(
        devices: List<DeviceEntity>,
        items: List<SharedItemEntity>
    ): Map<String, DeviceStream> {
        val itemsByDevice = items.groupBy { it.originDeviceId }
        return devices.associate { device ->
            val deviceItems = itemsByDevice[device.deviceId].orEmpty()
            val latestText = deviceItems.firstOrNull { it.categoryType == "TEXT" }
            device.deviceId to DeviceStream(
                deviceId = device.deviceId,
                clipboard = ClipboardEntry(
                    text = latestText?.metaContent.orEmpty(),
                    updatedLabel = latestText?.timestamp?.let { relativeTimeLabel(it) } ?: "No clipboard yet",
                    sourceApp = if (latestText == null) "Waiting" else "Clipboard"
                ),
                media = deviceItems
                    .filter { it.categoryType == "MEDIA" }
                    .map { it.toAsset() },
                documents = deviceItems
                    .filter { it.categoryType == "DOCUMENT" }
                    .map { it.toAsset() },
                storageUsedPercent = 0,
                lastSeenLabel = relativeTimeLabel(device.lastActiveTimestamp)
            )
        }
    }

    private fun SharedItemEntity.toAsset(): SyncAsset {
        val assetType = when {
            mimeType.startsWith("image/") -> SyncAssetType.IMAGE
            mimeType.startsWith("video/") -> SyncAssetType.VIDEO
            mimeType == "application/pdf" -> SyncAssetType.PDF
            mimeType.contains("zip") -> SyncAssetType.ARCHIVE
            else -> SyncAssetType.DOCUMENT
        }
        return SyncAsset(
            id = itemId,
            title = metaContent.substringBefore('|').ifBlank { metaContent },
            subtitle = metaContent.substringAfter('|', missingDelimiterValue = mimeType),
            type = assetType,
            syncState = runCatching { SyncTransferState.valueOf(syncState) }.getOrDefault(SyncTransferState.THUMBNAIL_ONLY)
        )
    }

    private fun DeviceEntity.toDeviceProfile(): DeviceProfile {
        return DeviceProfile(
            id = deviceId,
            name = deviceName,
            type = runCatching { DeviceType.valueOf(deviceType) }.getOrDefault(DeviceType.DESKTOP),
            isOnline = nowMillis() - lastActiveTimestamp < ONLINE_WINDOW_MILLIS
        )
    }

    private fun relativeTimeLabel(timestamp: Long): String {
        val age = (nowMillis() - timestamp).coerceAtLeast(0)
        val seconds = age / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            seconds < 5 -> "Just now"
            seconds < 60 -> "$seconds sec ago"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            else -> "Earlier"
        }
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val ONLINE_WINDOW_MILLIS = 2 * 60 * 1000L
    }
}
