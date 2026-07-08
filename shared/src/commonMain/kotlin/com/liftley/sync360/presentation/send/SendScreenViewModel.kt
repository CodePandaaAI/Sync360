package com.liftley.sync360.presentation.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.OutgoingRequestsController
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.repository.FilesManager
import com.liftley.sync360.presentation.send.model.FileSendState
import com.liftley.sync360.presentation.send.model.PickedFile
import com.liftley.sync360.presentation.send.model.toNearbyDeviceUiModel
import com.liftley.sync360.presentation.send.model.SendScreenState
import com.liftley.sync360.presentation.send.model.SendTab
import com.liftley.sync360.presentation.send.model.TextSendState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendScreenViewModel(
    private val filesManager: FilesManager,
    private val networkServicesController: NetworkServicesController,
    private val outgoingRequestsController: OutgoingRequestsController,
) : ViewModel() {
    private val _screenState: MutableStateFlow<SendScreenState> =
        MutableStateFlow(SendScreenState())
    val screenState: StateFlow<SendScreenState> = _screenState.asStateFlow()

    private var latestNearbyDevices: List<NearbyDevice> = emptyList()

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


    suspend fun sendTextToDevice(deviceId: String) {
        val device = latestNearbyDevices.firstOrNull { it.id == deviceId } ?: return

        val text = screenState.value.textInput

        if (text.isBlank()) return

        _screenState.update {
            it.copy(textSendState = TextSendState.Sending(device.deviceName, text))
        }

        val result = outgoingRequestsController.sendTextOffer(device, text)

        result.fold(
            onSuccess = {
                _screenState.update {
                    it.copy(textSendState = TextSendState.Sent(device.deviceName, text))
                }
            },
            onFailure = { error ->
                _screenState.update {
                    it.copy(textSendState = TextSendState.Failed(error.message ?: "Text not sent"))
                }
            }
        )
    }

    suspend fun sendFilesToDevice(deviceId: String) {
        val device = latestNearbyDevices.firstOrNull { it.id == deviceId } ?: return

        val files = _screenState.value.files

        if (files.isEmpty()) return

        _screenState.update {
            it.copy(fileSendState = FileSendState.SendingOffer(device.deviceName, files.size))
        }

        val result = outgoingRequestsController.sendFileOffer(device, files)

        result.fold(
            onSuccess = {
                _screenState.update {
                    it.copy(fileSendState = FileSendState.OfferAccepted(device.deviceName, files.size))
                }
            },
            onFailure = { error ->
                _screenState.update {
                    it.copy(fileSendState = FileSendState.Failed(error.message ?: "File offer not sent"))
                }
            }
        )
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

    fun resetTextSendState() {
        _screenState.update {
            it.copy(textSendState = TextSendState.Idle)
        }
    }

    fun handleFilesSelected(rawPlatformFiles: List<Any>) {
        viewModelScope.launch {
            // Move the file metadata resolution entirely off the Main/UI thread
            val parsedFiles = withContext(Dispatchers.IO) {
                filesManager.processPickedFiles(rawPlatformFiles)
            }

            // Update your UI state safely with the clean platform-independent data
            _screenState.update { currentState ->
                currentState.copy(files = _screenState.value.files + parsedFiles)
            }
        }
    }

    fun clearSelectedFiles() {
        _screenState.update { currentState ->
            currentState.copy(files = emptyList())
        }
    }

    fun removeSelectedFileFromList(file: PickedFile){
        _screenState.update { currentState ->
            currentState.copy(files = currentState.files - file)
        }
    }
}