package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.IncomingFileOffer
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch

data class SyncUiState(
    val isDesktop: Boolean = false,
    val serverIp: String = "127.0.0.1",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectedDevices: List<DeviceProfile> = emptyList(),
    val nearbyDevices: List<DeviceProfile> = emptyList(),
    val pendingPairingRequests: List<DeviceProfile> = emptyList(),
    val activeDeviceId: String? = null,
    val deviceStreams: Map<String, DeviceStream> = emptyMap(),
    val localNetworkHealthy: Boolean = true,
    val outgoingText: String = "",
    val pendingConnectDevice: DeviceProfile? = null,
    val userMessage: String? = null,
    val isScanningForDevices: Boolean = true,
    val selectedFiles: List<PickedFile> = emptyList(),
    val incomingFileOffer: IncomingFileOffer? = null,
    val receivedFileBatch: ReceivedFileBatch? = null
) {
    val activeClientCount: Int get() = connectedDevices.size
}

fun SyncUiState.allKnownDevices(): List<DeviceProfile> {
    val merged = linkedMapOf<String, DeviceProfile>()
    connectedDevices.forEach { merged[it.id] = it }
    nearbyDevices.forEach { nearby ->
        merged[nearby.id] = nearby.copy(
            name = merged[nearby.id]?.name ?: nearby.name,
            hostAddress = nearby.hostAddress ?: merged[nearby.id]?.hostAddress
        )
    }
    return merged.values.toList()
}

fun SyncUiState.activeDevice(): DeviceProfile? =
    activeDeviceId?.let { id -> allKnownDevices().firstOrNull { it.id == id } }
