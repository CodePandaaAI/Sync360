package com.liftley.sync360.presentation.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.OutgoingRequestsController
import com.liftley.sync360.data.file.SelectedFileReader
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.presentation.send.model.SendScreenState
import com.liftley.sync360.presentation.send.model.SendOperationState
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

class SendScreenViewModel(
    private val selectedFileReader: SelectedFileReader,
    private val networkServicesController: NetworkServicesController,
    private val outgoingRequestsController: OutgoingRequestsController,
) : ViewModel() {
    private val _screenState: MutableStateFlow<SendScreenState> =
        MutableStateFlow(SendScreenState())
    val screenState: StateFlow<SendScreenState> = _screenState.asStateFlow()

    private var latestNearbyDevices: List<NearbyDevice> = emptyList()
    private var activeSendJob: Job? = null

    init {
        viewModelScope.launch {
            networkServicesController.startNetworkServices()
        }

        viewModelScope.launch {
            networkServicesController.nearbyDevices.collect { devices ->
                latestNearbyDevices = devices

                _screenState.update {
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
                _screenState.update {
                    it.copy(discoveryStatus = status)
                }
            }
        }
    }


    fun restartDiscoveryServices() {
        viewModelScope.launch {
            networkServicesController.restartDiscoveryServices()
        }
    }


    fun sendTextToDevice(deviceId: String) {
        if (_screenState.value.sendOperationState != SendOperationState.Idle) {
            return
        }

        val device = latestNearbyDevices.firstOrNull { it.id == deviceId } ?: return

        val text = screenState.value.textInput

        if (text.isBlank()) return

        _screenState.update {
            it.copy(
                sendOperationState = SendOperationState.SendingTextOffer(
                    deviceName = device.deviceName
                )
            )
        }

        startSendJob {
            val result = outgoingRequestsController.sendTextOffer(device, text)
            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = {
                    _screenState.update {
                        it.copy(
                            sendOperationState = SendOperationState.TextSent(
                                deviceName = device.deviceName
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _screenState.update {
                        it.copy(
                            sendOperationState = SendOperationState.OperationFailed(
                                reason = error.message ?: "Text not sent"
                            )
                        )
                    }
                }
            )
        }
    }

    fun sendFilesToDevice(deviceId: String) {
        if (_screenState.value.sendOperationState != SendOperationState.Idle) {
            return
        }

        val device = latestNearbyDevices.firstOrNull { it.id == deviceId } ?: return

        val files = _screenState.value.files

        if (files.isEmpty()) return

        _screenState.update {
            it.copy(
                sendOperationState = SendOperationState.SendingFileOffer(
                    deviceName = device.deviceName,
                    fileCount = files.size
                )
            )
        }

        startSendJob {
            val result = outgoingRequestsController.sendFiles(
                device = device,
                selectedFiles = files,
                onFileStarted = { fileIndex, file ->
                    _screenState.update {
                        it.copy(
                            sendOperationState = SendOperationState.SendingFile(
                                deviceName = device.deviceName,
                                fileName = file.displayName,
                                fileNumber = fileIndex + 1,
                                totalFiles = files.size
                            )
                        )
                    }
                }
            )
            currentCoroutineContext().ensureActive()

            result.fold(
                onSuccess = {
                    _screenState.update {
                        it.copy(
                            sendOperationState = SendOperationState.FilesSent(
                                deviceName = device.deviceName,
                                fileCount = files.size
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _screenState.update {
                        it.copy(
                            sendOperationState = SendOperationState.OperationFailed(
                                reason = error.message ?: "Files not sent"
                            )
                        )
                    }
                }
            )
        }
    }

    fun cancelSend() {
        activeSendJob?.cancel()
        activeSendJob = null
        outgoingRequestsController.cancelCurrentFileTransfer()

        _screenState.update {
            it.copy(sendOperationState = SendOperationState.Cancelled)
        }
    }

    fun onTextChanged(text: String) {
        _screenState.update {
            it.copy(textInput = text)
        }
    }

    fun onTabSelected(tab: SendTab) {
        _screenState.update {
            it.copy(selectedTab = tab)
        }
    }

    fun clearSendOperation() {
        _screenState.update {
            it.copy(sendOperationState = SendOperationState.Idle)
        }
    }

    fun handleFilesSelected(rawPlatformFiles: List<Any>) {
        viewModelScope.launch {
            val parsedFiles = withContext(Dispatchers.IO) {
                selectedFileReader.readSelectedFiles(rawPlatformFiles)
            }

            _screenState.update { currentState ->
                currentState.copy(files = currentState.files + parsedFiles)
            }
        }
    }

    fun clearSelectedFiles() {
        _screenState.update { currentState ->
            currentState.copy(files = emptyList())
        }
    }

    fun removeSelectedFileFromList(file: SelectedFile){
        _screenState.update { currentState ->
            currentState.copy(files = currentState.files - file)
        }
    }

    private fun startSendJob(block: suspend () -> Unit) {
        val sendJob = viewModelScope.launch {
            block()
        }

        activeSendJob = sendJob
        sendJob.invokeOnCompletion {
            if (activeSendJob === sendJob) {
                activeSendJob = null
            }
        }
    }
}
