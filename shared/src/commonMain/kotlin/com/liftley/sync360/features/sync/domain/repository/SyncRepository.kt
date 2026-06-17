package com.liftley.sync360.features.sync.domain.repository

import com.liftley.sync360.features.sync.domain.model.ConnectionEvent
import com.liftley.sync360.features.sync.domain.model.ConnectionSnapshot
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import com.liftley.sync360.features.sync.domain.model.SyncStartResult
import com.liftley.sync360.features.sync.domain.model.SessionSnapshot
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import kotlinx.coroutines.flow.Flow

interface DeviceConnectionRepository {
    val nearbyDevices: Flow<List<DeviceProfile>>
    val connectionEvents: Flow<ConnectionEvent>
    val connectionSnapshot: Flow<ConnectionSnapshot>
    val sessionSnapshot: Flow<SessionSnapshot>
    val quickSaveEnabled: Flow<Boolean>
    val pendingIncomingOffer: Flow<PendingIncomingOffer?>
    val isScanning: Flow<Boolean>

    fun startSync(): SyncStartResult
    fun stopSync()
    fun shutdownSync()
    fun requestConnect(device: DeviceProfile)
    fun requestConnectByHost(hostAddress: String)
    fun confirmOutgoingConnect()
    fun dismissOutgoingConnect()
    fun acceptIncomingConnect(deviceId: String)
    fun declineIncomingConnect(deviceId: String)
    fun setQuickSaveEnabled(enabled: Boolean)
    fun acceptIncomingOffer(offerId: String)
    fun declineIncomingOffer(offerId: String)

    fun hasPeerGrantFor(deviceId: String): Boolean
    fun disconnectAll()

    suspend fun clearAllData()
}

interface MessageRepository {
    val sessionMessages: Flow<List<SyncMessage>>

    fun saveReceivedText(senderDeviceId: String, senderName: String, text: String)
}

interface FileTransferRepository {
    val transferSnapshot: Flow<TransferSnapshot>

    fun offerItems(items: List<SendItem>)
    fun offerItemsTo(deviceId: String, items: List<SendItem>)
    fun dismissReceivedFiles()
    fun dismissTransferFailure()
    fun cancelTransfer()
}

interface SyncRepository : DeviceConnectionRepository, MessageRepository, FileTransferRepository
