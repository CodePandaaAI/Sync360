package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.core.platform.ClipboardOperations
import com.liftley.sync360.core.platform.FileOperations
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.runtime.SyncRuntimeController
import com.liftley.sync360.features.sync.domain.controller.SyncConnectionController
import com.liftley.sync360.features.sync.domain.controller.SyncTransferController
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState
import com.liftley.sync360.features.sync.domain.model.SessionSnapshot
import com.liftley.sync360.features.sync.domain.model.SessionSecurityMode
import com.liftley.sync360.features.sync.domain.model.ConnectionEvent
import com.liftley.sync360.features.sync.domain.model.UserFacingFailure
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
    private val localIpAddress: String,
    private val localDeviceName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SyncUiState(
            localDeviceName = localDeviceName,
            serverIp = localIpAddress
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()
    private val _uiEffects = Channel<SyncUiEffect>(Channel.BUFFERED)
    val uiEffects: Flow<SyncUiEffect> = _uiEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            runtimeController.snapshot.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        runtimeState = snapshot.runtime,
                        securityMode = (snapshot.session as? SessionSnapshot.Approved)
                            ?.securityMode
                            ?: SessionSecurityMode.TRUSTED_LAN_PLAINTEXT,
                        pendingIncomingOffer = snapshot.pendingIncomingOffer,
                        quickSaveEnabled = snapshot.quickSaveEnabled,
                        fileTransferProgress = snapshot.transfer.progress,
                        fileTransferFailure = snapshot.transfer.failure,
                        receivedFileBatch = snapshot.transfer.receivedBatch,
                        localNetworkHealthy = snapshot.runtime is SyncRuntimeState.Ready
                    )
                }
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
                    is ConnectionEvent.WaitingForApproval ->
                        showMessage("Waiting for ${event.deviceName}")
                    is ConnectionEvent.Connected ->
                        showMessage("${event.deviceName} is available")
                    is ConnectionEvent.Failed ->
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
            is SyncEvent.CopyClipboard -> {
                if (event.text.isNotBlank()) {
                    clipboardOperations.writeClipboard(event.text)
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
                val merged = (_uiState.value.selectedItems + event.files.map { SendItem.File(it) })
                    .take(SyncProtocolLimits.MAX_FILES_PER_TRANSFER)
                _uiState.update { it.copy(selectedItems = merged) }
            }
            is SyncEvent.AddCustomText -> {
                if (event.text.isNotBlank()) {
                    val textItem = event.text.toSendTextItem()
                    val merged = (_uiState.value.selectedItems + textItem)
                        .take(SyncProtocolLimits.MAX_FILES_PER_TRANSFER)
                    _uiState.update { it.copy(selectedItems = merged, outgoingText = "") }
                }
            }
            SyncEvent.SendSelectedItems -> {
                val selected = _uiState.value.selectedItems
                if (selected.isNotEmpty()) {
                    transferController.sendItems(selected)
                }
                _uiState.update { it.copy(selectedItems = emptyList()) }
            }
            is SyncEvent.ProposeSendTo -> {
                if (_uiState.value.selectedItems.isEmpty()) {
                    showMessage("Add files or text first")
                    return
                }
                val target = findDevice(event.deviceId)
                if (target != null) {
                    _uiState.update { it.copy(pendingOutgoingOfferTarget = target) }
                }
            }
            SyncEvent.CancelSendProposal -> {
                _uiState.update { it.copy(pendingOutgoingOfferTarget = null) }
            }
            is SyncEvent.SendSelectedItemsTo -> {
                _uiState.update { it.copy(pendingOutgoingOfferTarget = null) }
                val selected = _uiState.value.selectedItems
                if (selected.isEmpty()) {
                    showMessage("Add files or text first")
                    return
                }
                transferController.sendItemsTo(event.deviceId, selected)
                _uiState.update { it.copy(selectedItems = emptyList()) }
            }
            SyncEvent.ClearSelectedItems -> {
                _uiState.update { it.copy(selectedItems = emptyList()) }
            }
            is SyncEvent.RemoveSelectedItem -> {
                val updated = _uiState.value.selectedItems.filter { it.id != event.itemId }
                _uiState.update { it.copy(selectedItems = updated) }
            }
            SyncEvent.DismissReceivedFiles -> transferController.dismissReceived()
            SyncEvent.DismissTransferFailure -> transferController.dismissFailure()
            SyncEvent.CancelTransfer -> transferController.cancel()
            is SyncEvent.OpenFile -> {
                if (event.path.isNotBlank()) {
                    val res = fileOperations.openFile(event.path)
                    if (res is com.liftley.sync360.core.platform.FileOperationResult.Failure) {
                        showMessage("Could not open file (it might have been moved or deleted)")
                    }
                }
            }
            is SyncEvent.ShowFileInFolder -> {
                if (event.path.isNotBlank()) {
                    val res = fileOperations.showFileInFolder(event.path)
                    if (res is com.liftley.sync360.core.platform.FileOperationResult.Failure) {
                        showMessage("Could not open containing folder")
                    }
                }
            }
            is SyncEvent.OpenDownloadsFolder -> {
                fileOperations.openDownloadsFolder()
            }
            SyncEvent.TriggerScan -> runtimeController.scan()
            SyncEvent.RestartSharing -> {
                runtimeController.restart()
                showMessage("Restarted local sharing")
            }
            is SyncEvent.AcceptIncomingOffer -> repository.acceptIncomingOffer(event.offerId)
            is SyncEvent.DeclineIncomingOffer -> repository.declineIncomingOffer(event.offerId)
            SyncEvent.ToggleQuickSave -> repository.setQuickSaveEnabled(!_uiState.value.quickSaveEnabled)
        }
    }

    private fun findDevice(deviceId: String): DeviceProfile? =
        _uiState.value.nearbyDevices.firstOrNull { it.id == deviceId }

    private fun showMessage(message: String) {
        _uiEffects.trySend(SyncUiEffect.ShowMessage(message))
    }

    private fun String.toSendTextItem(): SendItem.Text {
        val normalized = replace("\n", " ").trim()
        val shortPreview = if (normalized.length > 25) {
            normalized.take(25) + "..."
        } else {
            normalized
        }
        return SendItem.Text(
            id = "text:${kotlin.time.Clock.System.now().toEpochMilliseconds()}",
            text = this,
            preview = shortPreview
        )
    }
}

private fun ConnectionEvent.Failed.toUiMessage(): String {
    val target = peer ?: "device"
    return when (reason) {
        UserFacingFailure.SERVER_UNAVAILABLE ->
            "Sync360 could not open or reach the sharing server"
        UserFacingFailure.INVALID_HOST ->
            "Enter a valid local IPv4 address or hostname, optionally with a port"
        UserFacingFailure.MISSING_ROUTE ->
            "This device does not have a reachable address"
        UserFacingFailure.UNREACHABLE ->
            "Could not reach $target. Check Wi-Fi and firewall settings."
        UserFacingFailure.REQUEST_TIMEOUT ->
            "$target did not respond"
        UserFacingFailure.APPROVAL_TIMEOUT ->
            "$target is not ready"
        UserFacingFailure.DECLINED ->
            "Request declined"
        UserFacingFailure.PEER_BUSY ->
            "$target is busy"
        UserFacingFailure.PROTOCOL_MISMATCH ->
            "$target uses an incompatible Sync360 version"
        UserFacingFailure.CLIENT_CLOSED ->
            "Sync360 networking is stopped"
        UserFacingFailure.TEXT_INVALID ->
            "Text is empty or too large"
        UserFacingFailure.TEXT_DELIVERY_FAILED ->
            "Text could not be delivered"
        UserFacingFailure.INCOMING_REQUEST_EXPIRED ->
            "Incoming request expired"
        UserFacingFailure.RECEIVER_DECLINED ->
            "Receiver declined the transfer."
        UserFacingFailure.UNKNOWN ->
            "Device is not ready. Try again."
    }
}
