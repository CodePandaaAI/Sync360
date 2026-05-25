package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.IncomingFileOffer
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry

data class SyncUiState(
    val serverIp: String = "127.0.0.1",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeDevice: DeviceProfile? = null,
    val nearbyDevices: List<DeviceProfile> = emptyList(),
    val pendingIncomingRequest: DeviceProfile? = null,
    val pendingOutgoingRequest: DeviceProfile? = null,
    val isScanningForDevices: Boolean = true,
    val outgoingText: String = "",
    val latestTexts: List<ClipboardEntry> = emptyList(),
    val selectedFiles: List<PickedFile> = emptyList(),
    val incomingFileOffer: IncomingFileOffer? = null,
    val fileTransferProgress: FileTransferProgress? = null,
    val receivedFileBatch: ReceivedFileBatch? = null,
    val userMessage: String? = null,
    val localNetworkHealthy: Boolean = true
) {
    // Computed legacy compatibility layer to avoid breaking UI components
    val connectedDevices: List<DeviceProfile> get() = activeDevice?.let { listOf(it) } ?: emptyList()
    val activeDeviceId: String? get() = activeDevice?.id
    val activeClientCount: Int get() = if (activeDevice != null) 1 else 0
    val pendingPairingRequests: List<DeviceProfile> get() = pendingIncomingRequest?.let { listOf(it) } ?: emptyList()
    val pendingConnectDevice: DeviceProfile? get() = pendingOutgoingRequest

    val deviceStreams: Map<String, DeviceStream> get() = activeDevice?.let { device ->
        mapOf(
            device.id to DeviceStream(
                deviceId = device.id,
                clipboard = latestTexts.firstOrNull() ?: ClipboardEntry("", ""),
                media = emptyList(),
                documents = emptyList(),
                storageUsedPercent = 0,
                lastSeenLabel = "Now",
                latestTexts = latestTexts
            )
        )
    } ?: emptyMap()
}

fun SyncUiState.allKnownDevices(): List<DeviceProfile> {
    val merged = linkedMapOf<String, DeviceProfile>()
    activeDevice?.let { merged[it.id] = it }
    nearbyDevices.forEach { nearby ->
        merged[nearby.id] = nearby.copy(
            name = merged[nearby.id]?.name ?: nearby.name,
            hostAddress = nearby.hostAddress ?: merged[nearby.id]?.hostAddress
        )
    }
    return merged.values.toList()
}

fun SyncUiState.activeDevice(): DeviceProfile? = activeDevice
