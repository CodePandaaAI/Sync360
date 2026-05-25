package com.liftley.sync360.features.sync.domain.repository

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SyncMessage
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    val pairedDevices: Flow<List<DeviceProfile>>
    val nearbyDevices: Flow<List<DeviceProfile>>
    val pendingIncomingConnectRequests: Flow<List<DeviceProfile>>
    val pendingOutgoingConnectDevice: Flow<DeviceProfile?>
    val connectionStatus: Flow<ConnectionStatus>
    val activeDeviceId: Flow<String?>
    val conversationMessages: Flow<List<SyncMessage>>
    val isScanning: Flow<Boolean>

    fun startSync()
    fun stopSync()
    fun triggerManualScan()

    fun requestConnect(device: DeviceProfile)
    fun confirmOutgoingConnect()
    fun dismissOutgoingConnect()
    fun acceptIncomingConnect(deviceId: String)
    fun declineIncomingConnect(deviceId: String)

    fun switchActiveDevice(deviceId: String)
    fun disconnectActivePeer()
    fun disconnectAll()

    fun sendText(text: String)
    fun sendFile(fileName: String, mimeType: String, content: ByteArray)

    suspend fun clearAllData()
    fun deleteDevice(deviceId: String)
}
