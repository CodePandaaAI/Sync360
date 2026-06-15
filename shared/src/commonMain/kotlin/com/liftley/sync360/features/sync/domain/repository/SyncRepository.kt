package com.liftley.sync360.features.sync.domain.repository

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import kotlinx.coroutines.flow.Flow

interface DeviceConnectionRepository {
    val sessionDevices: Flow<List<DeviceProfile>>
    val nearbyDevices: Flow<List<DeviceProfile>>
    val pendingIncomingConnectRequests: Flow<List<DeviceProfile>>
    val pendingOutgoingConnectDevice: Flow<DeviceProfile?>
    val connectionStatus: Flow<ConnectionStatus>
    val activeDeviceId: Flow<String?>
    val isScanning: Flow<Boolean>

    fun startSync()
    fun stopSync()
    fun triggerManualScan()

    fun requestConnect(device: DeviceProfile)
    fun requestConnectByHost(hostAddress: String)
    fun confirmOutgoingConnect()
    fun dismissOutgoingConnect()
    fun acceptIncomingConnect(deviceId: String)
    fun declineIncomingConnect(deviceId: String)

    fun switchActiveDevice(deviceId: String)
    fun disconnectActivePeer()
    fun disconnectAll()

    suspend fun clearAllData()
    fun deleteDevice(deviceId: String)
}

interface MessageRepository {
    val sessionMessages: Flow<List<SyncMessage>>

    fun sendText(text: String)
}

interface FileTransferRepository {
    val fileTransferProgress: Flow<FileTransferProgress?>
    val fileTransferFailure: Flow<FileTransferFailure?>
    val receivedFileBatch: Flow<ReceivedFileBatch?>

    fun offerFiles(files: List<PickedFile>)
    fun dismissReceivedFiles()
    fun dismissTransferFailure()
}

interface SyncRepository : DeviceConnectionRepository, MessageRepository, FileTransferRepository
