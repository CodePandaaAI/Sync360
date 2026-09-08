package com.liftley.sync360.presentation.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.IncomingServerRequestsController
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.OutgoingRequestsController
import com.liftley.sync360.data.file.SelectedFileReader
import com.liftley.sync360.domain.model.FileReceiveCode
import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.domain.model.TextDeliveryLimits
import com.liftley.sync360.presentation.send.model.FileReceiveCodePrompt
import com.liftley.sync360.presentation.send.model.SendState
import com.liftley.sync360.presentation.send.model.SendScreenState
import com.liftley.sync360.presentation.send.model.SendTab
import com.liftley.sync360.presentation.send.model.toNearbyDeviceUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class SendScreenViewModel(
    private val selectedFileReader: SelectedFileReader,
    private val networkServicesController: NetworkServicesController,
    private val outgoingRequestsController: OutgoingRequestsController,
    incomingServerRequestsController: IncomingServerRequestsController
) : ViewModel() {
    private val _sendScreenState: MutableStateFlow<SendScreenState> =
        MutableStateFlow(
            SendScreenState(
                fileReceiveCode = incomingServerRequestsController.fileReceiveCode
            )
        )
    val sendScreenState: StateFlow<SendScreenState> = _sendScreenState.asStateFlow()

    private var latestNearbyDevices: List<NearbyDevice> = emptyList()
    private var activeSendJob: Job? = null

    private var activeFileSend: ActiveFileSend? = null

    init {
        viewModelScope.launch {
            networkServicesController.isDiscoveryEnabled.collect { enabled ->
                _sendScreenState.update { it.copy(isDiscoveryEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            networkServicesController.discoveryErrorMessage.collect { error ->
                _sendScreenState.update { it.copy(discoveryErrorMessage = error) }
            }
        }

        viewModelScope.launch {
            networkServicesController.nearbyDevices.collect { devices ->
                latestNearbyDevices = devices

                _sendScreenState.update {
                    it.copy(
                        nearbyDevices = devices.map { device ->
                            device.toNearbyDeviceUiModel()
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            networkServicesController.discoveryServiceStatus.collect { status ->
                _sendScreenState.update {
                    it.copy(discoveryStatus = status)
                }
            }
        }

        viewModelScope.launch {
            networkServicesController.registrationServiceStatus.collect { status ->
                _sendScreenState.update {
                    it.copy(registrationStatus = status)
                }
            }
        }
    }


    fun setDiscoveryEnabled(enabled: Boolean) {
        networkServicesController.setDiscoveryEnabled(enabled)
    }

    fun retryDiscovery() {
        networkServicesController.setDiscoveryEnabled(sendScreenState.value.isDiscoveryEnabled)
    }

    fun sendToDevice(deviceId: String) {
        val state = _sendScreenState.value
        if (!state.isContentReadyToSend) return

        when (state.selectedTab) {
            SendTab.Text -> sendTextToDevice(deviceId)
            SendTab.Files -> showFileReceiveCodePrompt(deviceId)
        }
    }

    private fun sendTextToDevice(deviceId: String) {
        if (_sendScreenState.value.sendState != SendState.Idle) {
            return
        }

        val deviceToSendText = latestNearbyDevices.firstOrNull { it.id == deviceId } ?: return

        val text = sendScreenState.value.textInput

        if (
            text.isBlank() ||
            text.length > TextDeliveryLimits.MAX_CHARACTER_COUNT
        ) {
            return
        }

        _sendScreenState.update {
            it.copy(
                sendState = SendState.SendingText(
                    deviceName = deviceToSendText.deviceName
                )
            )
        }

        startSendJob {
            val result = outgoingRequestsController.sendText(
                deviceToSendText = deviceToSendText,
                text = text
            )

            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = {
                    _sendScreenState.update {
                        it.copy(
                            sendState = SendState.TextSent(
                                deviceName = deviceToSendText.deviceName
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _sendScreenState.update {
                        it.copy(
                            sendState = SendState.Failed(
                                reason = error.message?.take(300) ?: "Text not sent"
                            )
                        )
                    }
                }
            )
        }
    }

    private fun showFileReceiveCodePrompt(deviceId: String) {
        val targetDevice = latestNearbyDevices.firstOrNull { device ->
            device.id == deviceId
        } ?: return

        _sendScreenState.update { state ->
            state.copy(
                fileReceiveCodePrompt = FileReceiveCodePrompt(
                    deviceId = targetDevice.id,
                    deviceName = targetDevice.deviceName,
                    fileCount = state.files.size
                )
            )
        }
    }

    private fun sendFilesToDevice(
        deviceId: String,
        receiveCode: String
    ) {
        if (_sendScreenState.value.sendState != SendState.Idle) {
            return
        }

        val deviceToSendFiles = latestNearbyDevices.firstOrNull { it.id == deviceId } ?: return

        val files = _sendScreenState.value.files

        if (files.isEmpty()) return

        val operationId = Uuid.random()

        activeFileSend = ActiveFileSend(
            operationId = operationId,
            targetDevice = deviceToSendFiles
        )

        val totalSizeBytes = files.sumOf { file -> file.sizeBytes ?: 0L }
        var currentFileIndex = 0
        var currentFileName = files.first().displayName
        var latestProgress = FileTransferProgress.waiting(totalSizeBytes)

        _sendScreenState.update {
            it.copy(
                sendState = SendState.PreparingFiles(
                    deviceName = deviceToSendFiles.deviceName,
                    fileCount = files.size
                )
            )
        }

        startSendJob {
            val result = outgoingRequestsController.sendFiles(
                deviceToSendFiles = deviceToSendFiles,
                selectedFiles = files,
                receiveCode = receiveCode,
                operationId = operationId,
                onFileStarted = { fileIndex, file ->
                    currentFileIndex = fileIndex
                    currentFileName = file.displayName
                    _sendScreenState.update {
                        it.copy(
                            sendState = SendState.SendingFile(
                                deviceName = deviceToSendFiles.deviceName,
                                fileName = file.displayName,
                                fileNumber = currentFileIndex + 1,
                                totalFiles = files.size,
                                progress = latestProgress
                            )
                        )
                    }
                },
                onProgress = { progress ->
                    latestProgress = progress
                    _sendScreenState.update {
                        it.copy(
                            sendState = SendState.SendingFile(
                                deviceName = deviceToSendFiles.deviceName,
                                fileName = currentFileName,
                                fileNumber = currentFileIndex + 1,
                                totalFiles = files.size,
                                progress = progress
                            )
                        )
                    }
                }
            )

            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = {
                    _sendScreenState.update {
                        it.copy(
                            sendState = SendState.FilesSent(
                                deviceName = deviceToSendFiles.deviceName,
                                fileCount = files.size
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _sendScreenState.update {
                        it.copy(
                            sendState = SendState.Failed(
                                reason = error.message?.take(300) ?: "Files not sent"
                            )
                        )
                    }
                }
            )
        }
    }

    fun cancelSend() {
        val operation = activeFileSend ?: return
        activeFileSend = null

        activeSendJob?.cancel()
        activeSendJob = null

        outgoingRequestsController.cancelCurrentFileTransfer()

        viewModelScope.launch {
            outgoingRequestsController.sendCancellationRequestToTargetDevice(
                targetDevice = operation.targetDevice,
                operationId = operation.operationId
            )
        }

        _sendScreenState.update {
            it.copy(sendState = SendState.Cancelled)
        }
    }

    fun onTextToSendChanged(text: String) {
        _sendScreenState.update {
            it.copy(textInput = text)
        }
    }

    fun onTabSelected(tab: SendTab) {
        _sendScreenState.update {
            it.copy(
                selectedTab = tab,
                fileReceiveCodePrompt = null
            )
        }
    }

    fun updateReceiveCode(code: String) {
        val normalizedCode = code
            .filter { character -> character in '0'..'9' }
            .take(FileReceiveCode.DIGIT_COUNT)

        _sendScreenState.update { state ->
            val prompt = state.fileReceiveCodePrompt ?: return@update state
            state.copy(
                fileReceiveCodePrompt = prompt.copy(code = normalizedCode)
            )
        }
    }

    fun dismissFileReceiveCodePrompt() {
        _sendScreenState.update {
            it.copy(fileReceiveCodePrompt = null)
        }
    }

    fun confirmFileReceiveCode() {
        val prompt = _sendScreenState.value.fileReceiveCodePrompt ?: return
        if (!FileReceiveCode.isValid(prompt.code)) return

        _sendScreenState.update {
            it.copy(fileReceiveCodePrompt = null)
        }

        sendFilesToDevice(
            deviceId = prompt.deviceId,
            receiveCode = prompt.code
        )
    }

    fun clearSendOperation() {
        _sendScreenState.update {
            it.copy(sendState = SendState.Idle)
        }
    }

    fun handleFilesSelected(rawPlatformFiles: List<Any>) {
        viewModelScope.launch {
            val parsedFiles = withContext(Dispatchers.Default) {
                selectedFileReader.readSelectedFiles(rawPlatformFiles)
            }

            _sendScreenState.update { currentState ->
                currentState.copy(
                    files = (currentState.files + parsedFiles).distinctBy { it.uri }
                )
            }
        }
    }

    fun clearSelectedFiles() {
        _sendScreenState.update { currentState ->
            currentState.copy(files = emptyList())
        }
    }

    fun removeSelectedFileFromList(file: SelectedFile) {
        _sendScreenState.update { currentState ->
            val remainingFiles = currentState.files - file
            currentState.copy(files = remainingFiles)
        }
    }

    private fun startSendJob(
        block: suspend () -> Unit
    ) {
        val sendJob = viewModelScope.launch {
            block()
        }

        activeSendJob = sendJob

        sendJob.invokeOnCompletion {
            if (activeSendJob === sendJob) {
                activeSendJob = null
                activeFileSend = null
            }
        }
    }

    private data class ActiveFileSend(
        val operationId: Uuid,
        val targetDevice: NearbyDevice
    )
}
