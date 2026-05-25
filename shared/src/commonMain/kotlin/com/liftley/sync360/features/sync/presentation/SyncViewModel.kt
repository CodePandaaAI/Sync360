package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SyncViewModel(
    val isDesktop: Boolean,
    private val repository: SyncRepository,
    private val platformOperations: PlatformOperations,
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
        repository.startSync()

        viewModelScope.launch {
            repository.pairedDevices.collect { paired ->
                _uiState.update { it.copy(connectedDevices = paired) }
            }
        }
        viewModelScope.launch {
            repository.nearbyDevices.collect { nearby ->
                _uiState.update { it.copy(nearbyDevices = nearby) }
            }
        }
        viewModelScope.launch {
            repository.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanningForDevices = scanning) }
            }
        }
        viewModelScope.launch {
            repository.pendingIncomingConnectRequests.collect { pending ->
                _uiState.update { it.copy(pendingPairingRequests = pending) }
            }
        }
        viewModelScope.launch {
            repository.pendingOutgoingConnectDevice.collect { pending ->
                _uiState.update { it.copy(pendingConnectDevice = pending) }
            }
        }
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.incomingFileOffer.collect { offer ->
                _uiState.update { it.copy(incomingFileOffer = offer) }
            }
        }
        viewModelScope.launch {
            repository.fileTransferProgress.collect { progress ->
                _uiState.update { it.copy(fileTransferProgress = progress) }
            }
        }
        viewModelScope.launch {
            repository.receivedFileBatch.collect { batch ->
                _uiState.update { it.copy(receivedFileBatch = batch) }
            }
        }
        viewModelScope.launch {
            combine(
                repository.activeDeviceId,
                repository.conversationMessages
            ) { activeId, messages ->
                val streams = if (activeId != null) {
                    mapOf(activeId to messages.toDeviceStream(activeId))
                } else {
                    emptyMap()
                }
                activeId to streams
            }.collect { (activeId, streams) ->
                _uiState.update {
                    it.copy(
                        activeDeviceId = activeId,
                        deviceStreams = streams
                    )
                }
            }
        }
    }

    fun onEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.Disconnect -> {
                repository.disconnectActivePeer()
                _uiState.update { it.copy(outgoingText = "") }
            }
            is SyncEvent.SendMessage -> {
                if (event.text.isNotBlank()) {
                    repository.sendText(event.text)
                }
                _uiState.update { it.copy(outgoingText = "") }
            }
            is SyncEvent.UpdateOutgoingText -> {
                _uiState.update { it.copy(outgoingText = event.text) }
            }
            is SyncEvent.RequestConnect -> {
                val device = findDevice(event.deviceId) ?: return
                repository.requestConnect(device)
            }
            is SyncEvent.ConfirmConnect -> repository.confirmOutgoingConnect()
            is SyncEvent.DismissConnectRequest -> repository.dismissOutgoingConnect()
            is SyncEvent.AcceptPairing -> repository.acceptIncomingConnect(event.deviceId)
            is SyncEvent.DeclinePairing -> repository.declineIncomingConnect(event.deviceId)
            is SyncEvent.SwitchDevice -> repository.switchActiveDevice(event.deviceId)
            is SyncEvent.CopyClipboard -> {
                val stream = _uiState.value.deviceStreams[event.deviceId]
                val latest = stream?.latestTexts?.firstOrNull()?.text
                if (!latest.isNullOrBlank()) {
                    platformOperations.writeClipboard(latest)
                    _uiState.update { it.copy(userMessage = "Copied to clipboard") }
                }
            }
            is SyncEvent.PasteFromClipboard -> {
                val clipText = platformOperations.readClipboard()
                if (!clipText.isNullOrBlank()) {
                    _uiState.update { it.copy(outgoingText = clipText) }
                }
            }
            is SyncEvent.OpenFilePicker -> {
                platformOperations.openFilePicker(event.kind) { files ->
                    onEvent(SyncEvent.AddSelectedFiles(files))
                }
            }
            is SyncEvent.AddSelectedFiles -> {
                val merged = (_uiState.value.selectedFiles + event.files).take(MAX_SELECTED_FILES)
                _uiState.update { it.copy(selectedFiles = merged) }
            }
            SyncEvent.SendSelectedFiles -> {
                val selected = _uiState.value.selectedFiles
                if (selected.isNotEmpty()) {
                    repository.offerFiles(selected)
                }
                _uiState.update {
                    it.copy(
                        selectedFiles = emptyList(),
                        userMessage = if (selected.isEmpty()) null else "Request sent"
                    )
                }
            }
            SyncEvent.ClearSelectedFiles -> {
                _uiState.update { it.copy(selectedFiles = emptyList()) }
            }
            is SyncEvent.AcceptFileOffer -> repository.acceptFileOffer(event.offerId)
            is SyncEvent.DeclineFileOffer -> repository.declineFileOffer(event.offerId)
            SyncEvent.DismissReceivedFiles -> repository.dismissReceivedFiles()
            is SyncEvent.ClearUserMessage -> {
                _uiState.update { it.copy(userMessage = null) }
            }
            is SyncEvent.OpenFile -> {
                if (event.path.isNotBlank()) {
                    platformOperations.openFile(event.path)
                }
            }
            SyncEvent.TriggerScan -> repository.triggerManualScan()
        }
    }

    private fun findDevice(deviceId: String): DeviceProfile? =
        _uiState.value.allKnownDevices().firstOrNull { it.id == deviceId }

    companion object {
        private const val MAX_SELECTED_FILES = 12
    }
}
