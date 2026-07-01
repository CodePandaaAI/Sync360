package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.remote.OutgoingRequestsController
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.presentation.featureSend.model.SendItem
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

    private var _sendScreenUiState: MutableStateFlow<SendScreenUiState> =
        MutableStateFlow(SendScreenUiState.Idle)
    val sendScreenUiState: StateFlow<SendScreenUiState> = _sendScreenUiState.asStateFlow()

    val nearbyDevices = networkServicesController.nearbyDevices

    val discoveryServiceStatus = networkServicesController.discoveryServiceStatus

    private val _sendingItemsList: MutableStateFlow<List<SendItem>> = MutableStateFlow(emptyList())
    val sendingItemsList: StateFlow<List<SendItem>> = _sendingItemsList.asStateFlow()

    fun restartDiscoveryServices() {
        viewModelScope.launch {
            networkServicesController.restartDiscoveryServices()
        }
    }

    fun resetState() {
        _sendScreenUiState.update { SendScreenUiState.Idle }
    }

    suspend fun onDeviceClick(device: NearbyDevice) {
        if (sendingItemsList.value.isNotEmpty()) {
            val firstText = sendingItemsList.value.first()
            viewModelScope.async {
                _sendScreenUiState.value =
                    SendScreenUiState.Sending(device.deviceName, (firstText as SendItem.Text).text)
                return@async outgoingRequestsController.offerRequestToPeer(
                    device,
                    firstText as SendItem.Text
                )

            }.await().fold(
                onSuccess = {
                    _sendScreenUiState.value = SendScreenUiState.Sent(
                        sentTo = device.deviceName,
                        (firstText as SendItem.Text).text
                    )
                },
                onFailure = {
                    _sendScreenUiState.value = SendScreenUiState.NotSent(it.message ?: "Not Sent")
                }
            )
        }
    }

    fun addSendItem(item: SendItem) {
        when (item) {
            is SendItem.Text -> {
                if (item.text.isEmpty()) {
                    return
                }
                _sendingItemsList.value += item
            }
        }
    }

    fun removeSendItem(item: SendItem) {
        _sendingItemsList.value -= item
    }

    fun clearAllSendItemList() {
        _sendingItemsList.value = emptyList()
    }
}