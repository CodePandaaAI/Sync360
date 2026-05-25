package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.SyncAsset
import com.liftley.sync360.features.sync.domain.model.SyncAssetType
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.model.SyncTransferState
import com.liftley.sync360.features.sync.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncViewModel(
    val isDesktop: Boolean,
    private val platformOperations: PlatformOperations,
    private val observePairedDevicesUseCase: ObservePairedDevicesUseCase,
    private val observeNearbyDevicesUseCase: ObserveNearbyDevicesUseCase,
    private val observePendingIncomingConnectUseCase: ObservePendingIncomingConnectUseCase,
    private val observePendingOutgoingConnectUseCase: ObservePendingOutgoingConnectUseCase,
    private val observeConnectionStatusUseCase: ObserveConnectionStatusUseCase,
    private val observeActiveDeviceIdUseCase: ObserveActiveDeviceIdUseCase,
    private val observeConversationMessagesUseCase: ObserveConversationMessagesUseCase,
    private val observeIsScanningUseCase: ObserveIsScanningUseCase,
    private val triggerManualScanUseCase: TriggerManualScanUseCase,
    private val requestConnectUseCase: RequestConnectUseCase,
    private val confirmOutgoingConnectUseCase: ConfirmOutgoingConnectUseCase,
    private val dismissOutgoingConnectUseCase: DismissOutgoingConnectUseCase,
    private val acceptIncomingConnectUseCase: AcceptIncomingConnectUseCase,
    private val declineIncomingConnectUseCase: DeclineIncomingConnectUseCase,
    private val switchActiveDeviceUseCase: SwitchActiveDeviceUseCase,
    private val sendTextUseCase: SendTextUseCase,
    private val sendFileUseCase: SendFileUseCase,
    private val disconnectActivePeerUseCase: DisconnectActivePeerUseCase,
    private val disconnectAllUseCase: DisconnectAllUseCase,
    private val localIpAddress: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SyncUiState(
            isDesktop = isDesktop,
            serverIp = localIpAddress,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            isScanningForDevices = true
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observePairedDevicesUseCase().collect { paired ->
                _uiState.update { it.copy(connectedDevices = paired) }
            }
        }
        viewModelScope.launch {
            observeNearbyDevicesUseCase().collect { nearby ->
                _uiState.update { it.copy(nearbyDevices = nearby) }
            }
        }
        viewModelScope.launch {
            observeIsScanningUseCase().collect { scanning ->
                _uiState.update { it.copy(isScanningForDevices = scanning) }
            }
        }
        viewModelScope.launch {
            observePendingIncomingConnectUseCase().collect { pending ->
                _uiState.update { it.copy(pendingPairingRequests = pending) }
            }
        }
        viewModelScope.launch {
            observePendingOutgoingConnectUseCase().collect { pending ->
                _uiState.update { it.copy(pendingConnectDevice = pending) }
            }
        }
        viewModelScope.launch {
            observeConnectionStatusUseCase().collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            observeActiveDeviceIdUseCase().collect { activeId ->
                _uiState.update { it.copy(activeDeviceId = activeId) }
            }
        }
        viewModelScope.launch {
            observeConversationMessagesUseCase().collect { messages ->
                val activeId = _uiState.value.activeDeviceId
                val streams = if (activeId != null) {
                    mapOf(activeId to messages.toDeviceStream(activeId))
                } else {
                    emptyMap()
                }
                _uiState.update {
                    it.copy(
                        messages = messages,
                        deviceStreams = streams
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectActivePeerUseCase()
    }

    fun onEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.Disconnect -> disconnectActivePeerUseCase()
            is SyncEvent.SendMessage -> {
                sendTextUseCase(event.text)
                _uiState.update { it.copy(outgoingText = "") }
            }
            is SyncEvent.UpdateOutgoingText -> {
                _uiState.update { it.copy(outgoingText = event.text) }
            }
            is SyncEvent.RequestConnect -> {
                val device = findDevice(event.deviceId) ?: return
                requestConnectUseCase(device)
            }
            is SyncEvent.ConfirmConnect -> confirmOutgoingConnectUseCase()
            is SyncEvent.DismissConnectRequest -> dismissOutgoingConnectUseCase()
            is SyncEvent.PairWithDevice -> {
                val device = findDevice(event.deviceId) ?: return
                requestConnectUseCase(device)
            }
            is SyncEvent.AcceptPairing -> acceptIncomingConnectUseCase(event.deviceId)
            is SyncEvent.DeclinePairing -> declineIncomingConnectUseCase(event.deviceId)
            is SyncEvent.SwitchDevice -> switchActiveDeviceUseCase(event.deviceId)
            is SyncEvent.CopyClipboard -> {
                val stream = _uiState.value.deviceStreams[event.deviceId]
                val latest = stream?.latestTexts?.firstOrNull()?.text
                    ?: _uiState.value.messages.lastOrNull { !it.isFromMe }?.text
                if (!latest.isNullOrBlank()) {
                    platformOperations.writeClipboard(latest)
                    _uiState.update { it.copy(userMessage = "Copied to clipboard") }
                }
            }
            is SyncEvent.SendFile -> {
                sendFileUseCase(event.name, event.mimeType, event.content)
                _uiState.update { it.copy(userMessage = "Sent ${event.name}") }
            }
            is SyncEvent.OpenFilePicker -> {
                platformOperations.openFilePicker(event.kind) { name, mime, bytes ->
                    sendFileUseCase(name, mime, bytes)
                    _uiState.update { it.copy(userMessage = "Sent $name") }
                }
            }
            is SyncEvent.ClearUserMessage -> {
                _uiState.update { it.copy(userMessage = null) }
            }
            is SyncEvent.OpenFile -> {
                if (event.path.isNotBlank()) {
                    platformOperations.openFile(event.path)
                }
            }
            SyncEvent.TriggerScan -> {
                triggerManualScanUseCase()
            }
            SyncEvent.AcceptFileOffer,
            SyncEvent.DeclineFileOffer,
            SyncEvent.SendCurrentClipboard,
            SyncEvent.PasteFromClipboard,
            is SyncEvent.SetOverlayEnabled,
            is SyncEvent.SetBackgroundMonitoringEnabled,
            is SyncEvent.OnIpChange,
            SyncEvent.Connect,
            is SyncEvent.ReceiveMessage,
            is SyncEvent.RequestDownload -> Unit
        }
    }

    private fun findDevice(deviceId: String): DeviceProfile? =
        allKnownDevices().firstOrNull { it.id == deviceId }

    private fun allKnownDevices(): List<DeviceProfile> {
        val state = _uiState.value
        val merged = linkedMapOf<String, DeviceProfile>()
        state.connectedDevices.forEach { merged[it.id] = it }
        state.nearbyDevices.forEach { nearby ->
            merged[nearby.id] = nearby.copy(
                name = merged[nearby.id]?.name ?: nearby.name,
                hostAddress = nearby.hostAddress ?: merged[nearby.id]?.hostAddress
            )
        }
        return merged.values.toList()
    }

    private fun List<SyncMessage>.toDeviceStream(peerId: String): DeviceStream {
        val texts = filter { !it.isFile }.map { msg ->
            ClipboardEntry(
                text = msg.text,
                updatedLabel = formatTime(msg.timestamp),
                sourceApp = if (msg.isFromMe) "You" else "Peer",
                isFromMe = msg.isFromMe
            )
        }
        val files = filter { it.isFile }.map { msg ->
            SyncAsset(
                id = msg.id,
                title = msg.fileName ?: msg.text,
                subtitle = formatTime(msg.timestamp),
                type = SyncAssetType.DOCUMENT,
                syncState = SyncTransferState.FULLY_DOWNLOADED,
                path = msg.text,
                isFromMe = msg.isFromMe
            )
        }
        return DeviceStream(
            deviceId = peerId,
            clipboard = texts.firstOrNull() ?: ClipboardEntry("", "", ""),
            media = emptyList(),
            documents = files,
            storageUsedPercent = 0,
            lastSeenLabel = "Now",
            latestTexts = texts
        )
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
