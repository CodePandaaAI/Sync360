package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.channels.Channel
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
            serverIp = localIpAddress,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            isScanningForDevices = true
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()
    private val _uiEffects = Channel<SyncUiEffect>(Channel.BUFFERED)
    val uiEffects: Flow<SyncUiEffect> = _uiEffects.receiveAsFlow()

    init {
        repository.startSync()

        viewModelScope.launch {
            combine(
                repository.activeDeviceId,
                repository.sessionDevices
            ) { activeId, sessionDevices ->
                sessionDevices.firstOrNull { it.id == activeId }
            }.collect { active ->
                _uiState.update { it.copy(activeDevice = active) }
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
                _uiState.update { it.copy(pendingIncomingRequest = pending.firstOrNull()) }
            }
        }
        viewModelScope.launch {
            repository.pendingOutgoingConnectDevice.collect { pending ->
                _uiState.update { it.copy(pendingOutgoingRequest = pending) }
            }
        }
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.fileTransferProgress.collect { progress ->
                _uiState.update { it.copy(fileTransferProgress = progress) }
            }
        }
        viewModelScope.launch {
            repository.fileTransferFailure.collect { failure ->
                _uiState.update { it.copy(fileTransferFailure = failure) }
                if (failure != null) {
                    showMessage(failure.message)
                }
            }
        }
        viewModelScope.launch {
            repository.receivedFileBatch.collect { batch ->
                _uiState.update { it.copy(receivedFileBatch = batch) }
            }
        }
        viewModelScope.launch {
            repository.sessionMessages.collect { messages ->
                val texts = messages.filter { !it.isFile && !it.isFromMe }
                    .takeLast(3) // LATEST_TEXT_LIMIT = 3
                    .asReversed()
                    .map { message ->
                        ClipboardEntry(
                            text = message.text,
                            updatedLabel = message.timestamp.toHourMinuteLabel(),
                            isFromMe = message.isFromMe
                        )
                    }
                _uiState.update { it.copy(latestTexts = texts) }
            }
        }
    }

    private fun Long.toHourMinuteLabel(): String =
        if (this <= 0L) "" else formatTimestampHourMinute(this)

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
            is SyncEvent.RequestConnectByHost -> repository.requestConnectByHost(event.hostAddress)
            is SyncEvent.ConfirmConnect -> repository.confirmOutgoingConnect()
            is SyncEvent.DismissConnectRequest -> repository.dismissOutgoingConnect()
            is SyncEvent.AcceptConnection -> repository.acceptIncomingConnect(event.deviceId)
            is SyncEvent.DeclineConnection -> repository.declineIncomingConnect(event.deviceId)
            is SyncEvent.SwitchDevice -> repository.switchActiveDevice(event.deviceId)
            is SyncEvent.CopyClipboard -> {
                val latest = _uiState.value.latestTexts.firstOrNull()?.text
                if (!latest.isNullOrBlank()) {
                    platformOperations.writeClipboard(latest)
                    showMessage("Copied to clipboard")
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
                    showMessage("Request sent")
                }
                _uiState.update { it.copy(selectedFiles = emptyList()) }
            }
            SyncEvent.ClearSelectedFiles -> {
                _uiState.update { it.copy(selectedFiles = emptyList()) }
            }
            SyncEvent.DismissReceivedFiles -> repository.dismissReceivedFiles()
            SyncEvent.DismissTransferFailure -> repository.dismissTransferFailure()
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

    private fun showMessage(message: String) {
        _uiEffects.trySend(SyncUiEffect.ShowMessage(message))
    }

    companion object {
        private const val MAX_SELECTED_FILES = 12
    }
}
