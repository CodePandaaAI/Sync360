package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.SessionSecurityMode
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState

data class SyncUiState(
    val serverIp: String = "127.0.0.1",
    val runtimeState: SyncRuntimeState = SyncRuntimeState.Stopped,
    val securityMode: SessionSecurityMode = SessionSecurityMode.TRUSTED_LAN_PLAINTEXT,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val activeDevice: DeviceProfile? = null,
    val nearbyDevices: List<DeviceProfile> = emptyList(),
    val pendingIncomingRequest: DeviceProfile? = null,
    val pendingOutgoingRequest: DeviceProfile? = null,
    val isScanningForDevices: Boolean = true,
    val outgoingText: String = "",
    val latestTexts: List<ClipboardEntry> = emptyList(),
    val selectedFiles: List<PickedFile> = emptyList(),
    val fileTransferProgress: FileTransferProgress? = null,
    val fileTransferFailure: FileTransferFailure? = null,
    val receivedFileBatch: ReceivedFileBatch? = null,
    val localNetworkHealthy: Boolean = true
)

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
