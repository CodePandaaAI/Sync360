package com.liftley.sync360.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SyncViewModel(
    val isDesktop: Boolean,
    private val observeDevicesUseCase: ObserveDevicesUseCase,
    private val observeConnectionStatusUseCase: ObserveConnectionStatusUseCase,
    private val observeActiveDeviceIdUseCase: ObserveActiveDeviceIdUseCase,
    private val observeRecentPayloadsUseCase: ObserveRecentPayloadsUseCase,
    private val startDiscoveryUseCase: StartDiscoveryUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val sendTextUseCase: SendTextUseCase,
    private val disconnectAllUseCase: DisconnectAllUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SyncUiState(
            isDesktop = isDesktop,
            serverIp = "0.0.0.0", // Simplified
            clientCount = 0,
            connectionStatus = ConnectionStatus.DISCONNECTED
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        // Start Ktor Server and Discovery symmetrically
        startDiscoveryUseCase()

        // Observe Devices (DB + Network)
        viewModelScope.launch {
            observeDevicesUseCase().collect { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
            }
        }

        // Observe Connection Status
        viewModelScope.launch {
            observeConnectionStatusUseCase().collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }
        
        // Observe Active Device
        viewModelScope.launch {
            observeActiveDeviceIdUseCase().collect { activeId ->
                _uiState.update { it.copy(activeDeviceId = activeId) }
            }
        }

        // Map Payloads to Messages (Simplified)
        viewModelScope.launch {
            observeRecentPayloadsUseCase().collect { payloads ->
                // Map Domain payloads to UI messages if needed
                // For simplicity we will assume the UI state mapping handles it or we update it here
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectAllUseCase()
    }

    fun onEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.Connect -> {
                // If typed manually
            }
            is SyncEvent.Disconnect -> {
                disconnectAllUseCase()
            }
            is SyncEvent.SendMessage -> {
                sendTextUseCase(event.text)
                _uiState.update { it.copy(outgoingText = "") }
            }
            is SyncEvent.UpdateOutgoingText -> {
                _uiState.update { it.copy(outgoingText = event.text) }
            }
            is SyncEvent.RequestConnect -> {
                val device = _uiState.value.connectedDevices.firstOrNull { it.id == event.deviceId }
                if (device != null) {
                    connectToDeviceUseCase(device)
                }
            }
            is SyncEvent.SwitchDevice -> {
                val device = _uiState.value.connectedDevices.firstOrNull { it.id == event.deviceId }
                if (device != null) {
                    connectToDeviceUseCase(device)
                }
            }
            // Removed ConnectManualIp to match clean rewrite
            else -> Unit // Ignored unused events for now in the simple rewrite
        }
    }
}
