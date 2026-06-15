package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.ConnectionSnapshot
import com.liftley.sync360.features.sync.domain.model.ConnectionFailure
import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DeviceSessionStore {
    private val _snapshot = MutableStateFlow(ConnectionSnapshot())
    val snapshot: StateFlow<ConnectionSnapshot> = _snapshot.asStateFlow()

    val activeDeviceIdValue: String?
        get() = snapshot.value.state.activeDeviceId()

    val pendingOutgoingValue: DeviceProfile?
        get() = snapshot.value.state.pendingDevice()

    val pendingIncomingValue: List<DeviceProfile>
        get() = snapshot.value.pendingIncoming

    val stateValue: ConnectionState
        get() = snapshot.value.state

    fun requestOutgoing(device: DeviceProfile) {
        updateState(ConnectionState.ResolvingRoute(device))
    }

    fun beginConnecting() {
        val device = pendingOutgoingValue ?: return
        updateState(ConnectionState.Requesting(device))
    }

    fun awaitApproval() {
        val device = pendingOutgoingValue ?: return
        updateState(ConnectionState.AwaitingApproval(device))
    }

    fun clearOutgoing() {
        if (snapshot.value.state is ConnectionState.Connected) return
        updateState(ConnectionState.Idle)
    }

    fun failConnecting(reason: ConnectionFailure) {
        val device = pendingOutgoingValue
        updateState(ConnectionState.Failed(device, reason))
    }

    fun fail(reason: ConnectionFailure) {
        updateState(ConnectionState.Failed(device = null, reason = reason))
    }

    fun addIncoming(device: DeviceProfile) {
        val current = snapshot.value
        if (current.pendingIncoming.none { it.id == device.id }) {
            _snapshot.value = current.copy(pendingIncoming = current.pendingIncoming + device)
        }
    }

    fun removeIncoming(deviceId: String): DeviceProfile? {
        val current = snapshot.value
        val device = current.pendingIncoming.firstOrNull { it.id == deviceId } ?: return null
        _snapshot.value = current.copy(
            pendingIncoming = current.pendingIncoming.filterNot { it.id == deviceId }
        )
        return device
    }

    fun connect(deviceId: String) {
        updateState(ConnectionState.Connected(deviceId))
    }

    fun beginDisconnecting() {
        val activeId = activeDeviceIdValue ?: return
        updateState(ConnectionState.Disconnecting(activeId))
    }

    fun disconnect() {
        _snapshot.value = ConnectionSnapshot()
    }

    private fun updateState(state: ConnectionState) {
        _snapshot.value = snapshot.value.copy(state = state)
    }
}

private fun ConnectionState.activeDeviceId(): String? = when (this) {
    is ConnectionState.Connected -> deviceId
    is ConnectionState.Disconnecting -> deviceId
    else -> null
}

private fun ConnectionState.pendingDevice(): DeviceProfile? = when (this) {
    is ConnectionState.ResolvingRoute -> device
    is ConnectionState.Requesting -> device
    is ConnectionState.AwaitingApproval -> device
    else -> null
}
