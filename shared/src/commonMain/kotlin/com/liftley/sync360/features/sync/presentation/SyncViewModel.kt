package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.core.platform.ClipboardOperations
import com.liftley.sync360.core.platform.FileOperations
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFailureReason
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.runtime.SyncRuntimeController
import com.liftley.sync360.features.sync.domain.controller.SyncTransferController

import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SyncViewModel(
    val isDesktop: Boolean,
    private val repository: SyncRepository,
    private val runtimeController: SyncRuntimeController,
    private val transferController: SyncTransferController,
    private val clipboardOperations: ClipboardOperations,
    private val fileOperations: FileOperations,
    private val localIpAddress: String,
    private val localDeviceName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SyncUiState(
            runtime = RuntimeUiState(
                localDeviceName = localDeviceName,
                serverIp = localIpAddress
            )
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()
    private val _uiEffects = Channel<SyncUiEffect>(Channel.BUFFERED)
    val uiEffects: Flow<SyncUiEffect> = _uiEffects.receiveAsFlow()
    private var shownSendingFailure: FileTransferFailure? = null

    init {
        viewModelScope.launch {
            runtimeController.snapshot.collect { snapshot ->
                val sendingFailure = snapshot.transfer.failure
                    ?.takeIf { it.direction == TransferDirection.SENDING }
                val sendingProgress = snapshot.transfer.progress
                    ?.takeIf { it.direction == TransferDirection.SENDING }
                if (sendingProgress != null || sendingFailure == null) {
                    shownSendingFailure = null
                }
                if (sendingFailure != null && sendingFailure != shownSendingFailure) {
                    shownSendingFailure = sendingFailure
                    showMessage(sendingFailure.snackbarMessage())
                    transferController.dismissFailure()
                }
                _uiState.update {
                    it.copy(
                        runtime = it.runtime.copy(
                            runtimeState = snapshot.runtime,
                            localNetworkHealthy = snapshot.runtime is SyncRuntimeState.Ready
                        ),
                        receive = it.receive.copy(
                            pendingIncomingOffer = snapshot.pendingIncomingOffer,
                            quickSaveEnabled = snapshot.quickSaveEnabled,
                            fileTransferProgress = snapshot.transfer.progress,
                            fileTransferFailure = snapshot.transfer.failure,
                            receivedFileBatch = snapshot.transfer.receivedBatch
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.nearbyDevices.collect { nearby ->
                _uiState.update { it.copy(discovery = it.discovery.copy(nearbyDevices = nearby)) }
            }
        }
        viewModelScope.launch {
            repository.clipboardHistory.collect { history ->
                _uiState.update { it.copy(send = it.send.copy(latestTexts = history)) }
            }
        }
        viewModelScope.launch {
            repository.isScanning.collect { scanning ->
                _uiState.update { it.copy(discovery = it.discovery.copy(isScanningForDevices = scanning)) }
            }
        }

    }

    private fun Long.toHourMinuteLabel(): String =
        if (this <= 0L) "" else formatTimestampHourMinute(this)

    fun onEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.UpdateOutgoingText -> {
                _uiState.update { it.copy(send = it.send.copy(outgoingText = event.text)) }
            }

            is SyncEvent.CopyClipboard -> {
                if (event.text.isNotBlank()) {
                    clipboardOperations.writeClipboard(event.text)
                    showMessage("Copied to clipboard")
                }
            }
            is SyncEvent.PasteFromClipboard -> {
                val clipText = clipboardOperations.readClipboard()
                if (!clipText.isNullOrBlank()) {
                    _uiState.update { it.copy(send = it.send.copy(outgoingText = clipText)) }
                }
            }
            is SyncEvent.OpenFilePicker -> {
                fileOperations.openFilePicker(event.kind) { files ->
                    onEvent(SyncEvent.AddSelectedFiles(files))
                }
            }
            is SyncEvent.AddSelectedFiles -> {
                val merged = (_uiState.value.send.selectedItems + event.files.map { SendItem.File(it) })
                    .take(SyncProtocolLimits.MAX_FILES_PER_TRANSFER)
                _uiState.update { it.copy(send = it.send.copy(selectedItems = merged)) }
            }
            is SyncEvent.AddCustomText -> {
                if (event.text.isNotBlank()) {
                    if (event.text.length > SyncProtocolLimits.MAX_TEXT_LENGTH) {
                        showMessage("Text is too large")
                        return
                    }
                    val textItem = event.text.toSendTextItem()
                    val merged = (_uiState.value.send.selectedItems + textItem)
                        .take(SyncProtocolLimits.MAX_FILES_PER_TRANSFER)
                    _uiState.update { it.copy(send = it.send.copy(selectedItems = merged, outgoingText = "")) }
                }
            }

            is SyncEvent.ProposeSendTo -> {
                if (_uiState.value.send.selectedItems.isEmpty()) {
                    showMessage("Add files or text first")
                    return
                }
                val target = findDevice(event.deviceId)
                if (target != null) {
                    _uiState.update { it.copy(send = it.send.copy(pendingOutgoingOfferTarget = target)) }
                }
            }
            SyncEvent.CancelSendProposal -> {
                _uiState.update { it.copy(send = it.send.copy(pendingOutgoingOfferTarget = null)) }
            }
            is SyncEvent.SendSelectedItemsTo -> {
                _uiState.update { it.copy(send = it.send.copy(pendingOutgoingOfferTarget = null)) }
                val selected = _uiState.value.send.selectedItems
                if (selected.isEmpty()) {
                    showMessage("Add files or text first")
                    return
                }
                transferController.sendItemsTo(event.deviceId, selected)
            }
            is SyncEvent.SendSelectedItemsToHost -> {
                val selected = _uiState.value.send.selectedItems
                if (selected.isEmpty()) {
                    showMessage("Add files or text first")
                    return
                }
                transferController.sendItemsToHost(event.hostAddress, selected)
            }
            SyncEvent.ClearSelectedItems -> {
                _uiState.update { it.copy(send = it.send.copy(selectedItems = emptyList())) }
            }
            is SyncEvent.RemoveSelectedItem -> {
                val updated = _uiState.value.send.selectedItems.filter { it.id != event.itemId }
                _uiState.update { it.copy(send = it.send.copy(selectedItems = updated)) }
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
            SyncEvent.ToggleQuickSave -> repository.setQuickSaveEnabled(!_uiState.value.receive.quickSaveEnabled)
        }
    }

    private fun findDevice(deviceId: String): DeviceProfile? =
        _uiState.value.discovery.nearbyDevices.firstOrNull { it.id == deviceId }

    private fun showMessage(message: String) {
        _uiEffects.trySend(SyncUiEffect.ShowMessage(message))
    }

    private fun FileTransferFailure.snackbarMessage(): String = when (reason) {
        TransferFailureReason.RECEIVER_CANCELLED -> "$peerName declined your request"
        TransferFailureReason.RECEIVER_BUSY -> "$peerName is busy"
        TransferFailureReason.RECEIVER_UNAVAILABLE -> "$peerName is unavailable"
        TransferFailureReason.TIMED_OUT -> "Transfer to $peerName timed out"
        TransferFailureReason.INVALID_SELECTION -> message
        TransferFailureReason.SOURCE_UNAVAILABLE -> "Selected file could not be read"
        else -> message.ifBlank { "Transfer to $peerName failed" }
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
