package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.remote.OutgoingRequestsController
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.remote.response.PingRequestResponse
import com.liftley.sync360.presentation.featureSend.model.SendScreenUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SendScreenViewModel(
    private val networkServicesController: NetworkServicesController,
    private val outgoingRequestsController: OutgoingRequestsController,
) : ViewModel() {
    init {
        viewModelScope.launch {
            networkServicesController.startNetworkServices()
        }
    }

    private var _sendScreenUiState: MutableStateFlow<SendScreenUiState> = MutableStateFlow(SendScreenUiState.Idle)
    val sendScreenUiState: StateFlow<SendScreenUiState> = _sendScreenUiState.asStateFlow()

    val nearbyDevices = networkServicesController.nearbyDevices

    val discoveryServiceStatus = networkServicesController.discoveryServiceStatus

    fun restartDiscoveryServices() {
        viewModelScope.launch {
            networkServicesController.restartDiscoveryServices()
        }
    }

    fun resetState() {
        _sendScreenUiState.update { SendScreenUiState.Idle }
    }

    suspend fun onDeviceClick(device: NearbyDevice): PingRequestResponse {
        _sendScreenUiState.value = SendScreenUiState.Sending(device.deviceName, "Ping Data")
        val result = viewModelScope.async {
         outgoingRequestsController.sendPingRequestToDevice(device)
        }.await()
        when(result){
            is PingRequestResponse.Accepted -> {
                _sendScreenUiState.value = SendScreenUiState.Sent(device.deviceName, "Ping Data")
            }

            is PingRequestResponse.Declined -> {
                _sendScreenUiState.value = SendScreenUiState.NotSent(result.reason)
            }
        }

        return result
    }
}