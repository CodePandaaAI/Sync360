package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.ClipboardOperations
import com.liftley.sync360.core.platform.FileOperations
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.runtime.SyncRuntimeController
import com.liftley.sync360.features.sync.domain.controller.SyncConnectionController
import com.liftley.sync360.features.sync.domain.controller.SyncTransferController
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState
import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.SessionSnapshot
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SyncViewModel(
    val isDesktop: Boolean,
    private val repository: SyncRepository,
    private val runtimeController: SyncRuntimeController,
    private val connectionController: SyncConnectionController,
    private val transferController: SyncTransferController,
    private val clipboardOperations: ClipboardOperations,
    private val fileOperations: FileOperations,
    private val localIpAddress: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SyncUiState(
            serverIp = localIpAddress,
            isScanningForDevices = true
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()
    private val _uiEffects = Channel<SyncUiEffect>(Channel.BUFFERED)
    val uiEffects: Flow<SyncUiEffect> = _uiEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            var lastTransferFailure: com.liftley.sync360.features.sync.domain.model.FileTransferFailure? = null
            runtimeController.snapshot.collect { snapshot ->
                val activeDevice = (snapshot.session as? SessionSnapshot.Approved)?.let { session ->
                    DeviceProfile(
                        id = session.identity.deviceId,
                        name = session.identity.name,
                        type = session.identity.type,
                        hostAddress = session.route.host,
                        port = session.route.port,
                        isOnline = true
                    )
                }
                val pendingOutgoing = when (val connection = snapshot.connection.state) {
                    is ConnectionState.ResolvingRoute -> connection.device
                    is ConnectionState.Requesting -> connection.device
                    is ConnectionState.AwaitingApproval -> connection.device
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        activeDevice = activeDevice,
                        runtimeState = snapshot.runtime,
                        securityMode = (snapshot.session as? SessionSnapshot.Approved)
                            ?.securityMode
                            ?: com.liftley.sync360.features.sync.domain.model.SessionSecurityMode.TRUSTED_LAN_PLAINTEXT,
                        pendingIncomingRequest = snapshot.connection.pendingIncoming.firstOrNull(),
                        pendingOutgoingRequest = pendingOutgoing,
                        connectionState = snapshot.connection.state,
                        fileTransferProgress = snapshot.transfer.progress,
                        fileTransferFailure = snapshot.transfer.failure,
                        receivedFileBatch = snapshot.transfer.receivedBatch,
                        localNetworkHealthy = snapshot.runtime is SyncRuntimeState.Ready,
                        isScanningForDevices = snapshot.runtime is SyncRuntimeState.Starting ||
                            it.isScanningForDevices
                    )
                }
                // Let the UI handle the failure card via uiState.fileTransferFailure
                lastTransferFailure = snapshot.transfer.failure
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
            repository.connectionEvents.collect { event ->
                when (event) {
                    is com.liftley.sync360.features.sync.domain.model.ConnectionEvent.WaitingForApproval ->
                        showMessage("Waiting for ${event.deviceName} to approve")
                    is com.liftley.sync360.features.sync.domain.model.ConnectionEvent.Connected ->
                        showMessage("Connected to ${event.deviceName}")
                    is com.liftley.sync360.features.sync.domain.model.ConnectionEvent.Failed ->
                        showMessage(event.toUiMessage())
                }
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
                connectionController.disconnectActive()
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
                connectionController.request(device)
            }
            is SyncEvent.RequestConnectByHost -> connectionController.requestByHost(event.hostAddress)
            is SyncEvent.ConfirmConnect -> connectionController.confirmRequest()
            is SyncEvent.DismissConnectRequest -> connectionController.dismissRequest()
            is SyncEvent.AcceptConnection -> connectionController.acceptIncoming(event.deviceId)
            is SyncEvent.DeclineConnection -> connectionController.declineIncoming(event.deviceId)
            is SyncEvent.SwitchDevice -> connectionController.switchActive(event.deviceId)
            is SyncEvent.CopyClipboard -> {
                val latest = _uiState.value.latestTexts.firstOrNull()?.text
                if (!latest.isNullOrBlank()) {
                    clipboardOperations.writeClipboard(latest)
                    showMessage("Copied to clipboard")
                }
            }
            is SyncEvent.PasteFromClipboard -> {
                val clipText = clipboardOperations.readClipboard()
                if (!clipText.isNullOrBlank()) {
                    _uiState.update { it.copy(outgoingText = clipText) }
                }
            }
            is SyncEvent.OpenFilePicker -> {
                fileOperations.openFilePicker(event.kind) { files ->
                    onEvent(SyncEvent.AddSelectedFiles(files))
                }
            }
            is SyncEvent.AddSelectedFiles -> {
                val merged = (_uiState.value.selectedFiles + event.files)
                    .take(SyncProtocolLimits.MAX_FILES_PER_TRANSFER)
                _uiState.update { it.copy(selectedFiles = merged) }
            }
            SyncEvent.SendSelectedFiles -> {
                val selected = _uiState.value.selectedFiles
                if (selected.isNotEmpty()) {
                    transferController.send(selected)
                }
                _uiState.update { it.copy(selectedFiles = emptyList()) }
            }
            SyncEvent.ClearSelectedFiles -> {
                _uiState.update { it.copy(selectedFiles = emptyList()) }
            }
            SyncEvent.DismissReceivedFiles -> transferController.dismissReceived()
            SyncEvent.DismissTransferFailure -> transferController.dismissFailure()
            SyncEvent.CancelTransfer -> transferController.cancel()
            is SyncEvent.OpenFile -> {
                if (event.path.isNotBlank()) {
                    fileOperations.openFile(event.path)
                }
            }
            is SyncEvent.ShowFileInFolder -> {
                if (event.path.isNotBlank()) {
                    fileOperations.showFileInFolder(event.path)
                }
            }
            is SyncEvent.OpenDownloadsFolder -> {
                fileOperations.openDownloadsFolder()
            }
            SyncEvent.TriggerScan -> runtimeController.scan()
        }
    }

    private fun findDevice(deviceId: String): DeviceProfile? =
        _uiState.value.allKnownDevices().firstOrNull { it.id == deviceId }

    private fun showMessage(message: String) {
        _uiEffects.trySend(SyncUiEffect.ShowMessage(message))
    }
}

private fun com.liftley.sync360.features.sync.domain.model.ConnectionEvent.Failed.toUiMessage(): String {
    val target = peer ?: "device"
    return when (reason) {
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.SERVER_UNAVAILABLE ->
            "Sync360 could not open or reach the sharing server"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.INVALID_HOST ->
            "Enter a valid local IPv4 address or hostname, optionally with a port"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.MISSING_ROUTE ->
            "This device does not have a reachable address"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.UNREACHABLE ->
            "Could not reach $target. Check Wi-Fi and firewall settings."
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.REQUEST_TIMEOUT ->
            "Connection to $target timed out"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.APPROVAL_TIMEOUT ->
            "$target did not approve the connection in time"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.DECLINED ->
            "Connection declined"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.PEER_BUSY ->
            "$target is busy"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.PROTOCOL_MISMATCH ->
            "$target uses an incompatible Sync360 version"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.CLIENT_CLOSED ->
            "Sync360 networking is stopped"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.TEXT_INVALID ->
            "Text is empty or too large"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.TEXT_DELIVERY_FAILED ->
            "Text could not be delivered"
        com.liftley.sync360.features.sync.domain.model.UserFacingFailure.UNKNOWN ->
            "Connection failed"
    }
}
