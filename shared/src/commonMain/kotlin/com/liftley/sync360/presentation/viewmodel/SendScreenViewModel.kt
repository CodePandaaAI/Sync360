package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.remote.OutgoingRequestsController
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.presentation.featureSend.model.SendScreenState
import com.liftley.sync360.presentation.featureSend.model.SendTab
import com.liftley.sync360.presentation.featureSend.model.TextSendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SendScreenViewModel(
    private val networkServicesController: NetworkServicesController,
    private val outgoingRequestsController: OutgoingRequestsController,
) : ViewModel() {
    private val _screenState: MutableStateFlow<SendScreenState> = MutableStateFlow(SendScreenState())
    val screenState: StateFlow<SendScreenState> = _screenState.asStateFlow()

    init {
        viewModelScope.launch {
            networkServicesController.startNetworkServices()
        }

        viewModelScope.launch {
            networkServicesController.nearbyDevices.collect { devices ->
                _screenState.update {
                    it.copy(nearbyDevices = devices)
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


    suspend fun sendTextToDevice(device: NearbyDevice) {
        val text = screenState.value.textInput

        if (text.isBlank()) return

        _screenState.update {
            it.copy(textSendState = TextSendState.Sending(device.deviceName, text))
        }

        val result = outgoingRequestsController.offerRequestToPeer(device, text)

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
}