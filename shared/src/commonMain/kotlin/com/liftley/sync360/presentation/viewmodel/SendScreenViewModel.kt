package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.remote.OutgoingRequestsController
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.remote.response.PingRequestResponse
import kotlinx.coroutines.async
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

    val nearbyDevices = networkServicesController.nearbyDevices

    val discoveryServiceStatus = networkServicesController.discoveryServiceStatus

    fun restartDiscoveryServices() {
        viewModelScope.launch {
            networkServicesController.restartDiscoveryServices()
        }
    }

    suspend fun onDeviceClick(device: NearbyDevice): PingRequestResponse {
        return viewModelScope.async {
         outgoingRequestsController.sendPingRequestToDevice(device)
        }.await()
    }
}