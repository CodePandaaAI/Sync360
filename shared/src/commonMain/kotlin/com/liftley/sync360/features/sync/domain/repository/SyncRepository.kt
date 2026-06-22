package com.liftley.sync360.features.sync.domain.repository

import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.domain.model.SyncStartResult
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    val nearbyDevices: Flow<List<DeviceProfile>>
    val isScanning: Flow<Boolean>
    val quickSaveEnabled: Flow<Boolean>
    val pendingIncomingOffer: Flow<PendingIncomingOffer?>
    val transferSnapshot: Flow<TransferSnapshot>
    val clipboardHistory: Flow<List<ClipboardEntry>>

    fun startSync(): SyncStartResult
    fun stopSync()
    fun shutdownSync()

    fun setQuickSaveEnabled(enabled: Boolean)
    fun acceptIncomingOffer(offerId: String)
    fun declineIncomingOffer(offerId: String)

    fun offerItemsTo(deviceId: String, items: List<SendItem>)
    fun offerItemsToHost(hostAddress: String, items: List<SendItem>)
    fun dismissReceivedFiles()
    fun dismissTransferFailure()
    fun cancelTransfer()

    suspend fun clearAllData()
}
