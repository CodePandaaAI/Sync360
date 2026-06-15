package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceSessionStore {
    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _pendingIncoming = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val pendingIncoming: StateFlow<List<DeviceProfile>> = _pendingIncoming.asStateFlow()

    private val _pendingOutgoing = MutableStateFlow<DeviceProfile?>(null)
    val pendingOutgoing: StateFlow<DeviceProfile?> = _pendingOutgoing.asStateFlow()

    fun requestOutgoing(device: DeviceProfile) {
        _pendingOutgoing.value = device
    }

    fun clearOutgoing() {
        _pendingOutgoing.value = null
    }

    fun addIncoming(device: DeviceProfile) {
        if (_pendingIncoming.value.none { it.id == device.id }) {
            _pendingIncoming.value += device
        }
    }

    fun removeIncoming(deviceId: String): DeviceProfile? {
        val device = _pendingIncoming.value.firstOrNull { it.id == deviceId } ?: return null
        _pendingIncoming.value = _pendingIncoming.value.filterNot { it.id == deviceId }
        return device
    }

    fun connect(deviceId: String) {
        _activeDeviceId.value = deviceId
        _connectionStatus.value = ConnectionStatus.CONNECTED
    }

    fun disconnect() {
        _activeDeviceId.value = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}
