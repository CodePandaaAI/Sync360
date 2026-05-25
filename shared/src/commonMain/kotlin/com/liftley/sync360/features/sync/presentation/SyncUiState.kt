package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SyncMessage

data class FileOffer(
    val senderId: String,
    val senderName: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val base64Data: String
)

data class SyncUiState(
    val isDesktop: Boolean = false,
    val serverIp: String = "127.0.0.1",
    val clientIpInput: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val messages: List<SyncMessage> = emptyList(),
    val clientCount: Int = 0,
    val connectedDevices: List<DeviceProfile> = emptyList(),
    val nearbyDevices: List<DeviceProfile> = emptyList(),
    val pendingPairingRequests: List<DeviceProfile> = emptyList(),
    val activeDeviceId: String? = null,
    val deviceStreams: Map<String, DeviceStream> = emptyMap(),
    val overlayEnabled: Boolean = false,
    val backgroundMonitoringEnabled: Boolean = true,
    val localNetworkHealthy: Boolean = true,
    val outgoingText: String = "",
    val pendingConnectDevice: DeviceProfile? = null,
    val pendingFileOffer: FileOffer? = null,
    val userMessage: String? = null,
    val isScanningForDevices: Boolean = true
)

/** All devices we know about (paired + live nearby), for resolving selection by id. */
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

