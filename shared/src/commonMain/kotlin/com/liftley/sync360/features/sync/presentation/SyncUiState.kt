package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.SessionSecurityMode
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState

data class SyncUiState(
    val localDeviceName: String = "This device",
    val serverIp: String = "127.0.0.1",
    val runtimeState: SyncRuntimeState = SyncRuntimeState.Stopped,
    val securityMode: SessionSecurityMode = SessionSecurityMode.TRUSTED_LAN_PLAINTEXT,
    val nearbyDevices: List<DeviceProfile> = emptyList(),
    val pendingIncomingOffer: PendingIncomingOffer? = null,
    val quickSaveEnabled: Boolean = false,
    val pendingOutgoingOfferTarget: DeviceProfile? = null,
    val isScanningForDevices: Boolean = false,
    val outgoingText: String = "",
    val latestTexts: List<ClipboardEntry> = emptyList(),
    val selectedItems: List<SendItem> = emptyList(),
    val fileTransferProgress: FileTransferProgress? = null,
    val fileTransferFailure: FileTransferFailure? = null,
    val receivedFileBatch: ReceivedFileBatch? = null,
    val localNetworkHealthy: Boolean = true
)
