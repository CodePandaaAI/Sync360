package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.core.database.SyncDatabase
import com.liftley.sync360.core.network.SyncPayload
import com.liftley.sync360.core.network.SyncPayloadCodec
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList

class SyncRepositoryImpl(
    private val networkService: SyncNetworkService,
    private val discoveryService: NetworkDiscoveryService,
    private val database: SyncDatabase,
    private val localDevice: DeviceProfile
) : SyncRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // State Tracking
    private val _activeDeviceId = MutableStateFlow<String?>(null)
    override val activeDeviceId: Flow<String?> = _activeDeviceId.asStateFlow()

    override val connectionStatus: Flow<ConnectionStatus> = networkService.connectionStatus

    // Unified Devices Stream (DB + Network)
    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    
    override val devices: Flow<List<DeviceProfile>> = combine(
        database.deviceEntityQueries.selectAllDevices().asFlow().mapToList(Dispatchers.Default),
        _nearbyDevices
    ) { dbDevices, nearby ->
        val merged = linkedMapOf<String, DeviceProfile>()
        
        dbDevices.forEach { entity ->
            merged[entity.deviceId] = DeviceProfile(
                id = entity.deviceId,
                name = entity.deviceName,
                type = DeviceType.valueOf(entity.deviceType),
                isOnline = false
            )
        }
        
        nearby.forEach { device ->
            val existing = merged[device.id]
            merged[device.id] = device.copy(
                name = existing?.name ?: device.name,
                isOnline = true
            )
        }
        
        merged.values.toList().sortedByDescending { it.isOnline }
    }

    override val recentPayloads: Flow<List<SyncPayload>> = 
        database.sharedItemEntityQueries.selectAllItems().asFlow().mapToList(Dispatchers.Default).map { entities ->
            entities.map { entity ->
                SyncPayload(
                    kind = entity.categoryType, // Simplified mapping
                    originDeviceId = entity.originDeviceId,
                    originDeviceName = "Unknown",
                    originDeviceType = "PHONE",
                    content = entity.metaContent,
                    timestamp = entity.timestamp
                )
            }
        }

    init {
        // Collect incoming payloads from network
        scope.launch {
            networkService.incomingPayloads.collect { json ->
                val payload = SyncPayloadCodec.decodeOrNull(json) ?: return@collect
                
                // Save to DB
                database.sharedItemEntityQueries.insertOrUpdateItem(
                    itemId = payload.timestamp.toString(), // Simplified ID
                    originDeviceId = payload.originDeviceId,
                    categoryType = payload.kind,
                    mimeType = "text/plain",
                    metaContent = payload.content,
                    thumbnailBytes = null,
                    syncState = "RECEIVED",
                    timestamp = payload.timestamp
                )
            }
        }

        // Collect network discovery
        scope.launch {
            discoveryService.startDiscovery()
            // Simplified: Just an example, a real implementation would collect from discoveryService flow
        }
    }

    override fun startDiscovery() {
        discoveryService.startDiscovery()
    }

    override fun stopDiscovery() {
        discoveryService.stopDiscovery()
    }

    override fun startServer() {
        networkService.startServer(8080)
    }

    override fun connectToDevice(device: DeviceProfile) {
        _activeDeviceId.value = device.id
        networkService.connectToPeer(device.connectionHost, 8080, localDevice.id)
    }

    override fun disconnectAll() {
        _activeDeviceId.value = null
        networkService.disconnectFromPeer()
        networkService.stopServer()
    }

    override fun sendText(text: String) {
        val payload = SyncPayload(
            kind = "clipboard",
            originDeviceId = localDevice.id,
            originDeviceName = localDevice.name,
            originDeviceType = localDevice.type.name,
            content = text,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            targetDeviceId = _activeDeviceId.value
        )
        
        val json = SyncPayloadCodec.encode(payload)
        networkService.sendToPeer(json)
        networkService.broadcastToClients(json) // Send symmetric
        
        // Save to DB
        scope.launch {
            database.sharedItemEntityQueries.insertOrUpdateItem(
                itemId = payload.timestamp.toString(),
                originDeviceId = localDevice.id,
                categoryType = payload.kind,
                mimeType = "text/plain",
                metaContent = payload.content,
                thumbnailBytes = null,
                syncState = "SENT",
                timestamp = payload.timestamp
            )
        }
    }

    override fun sendFile(fileName: String, mimeType: String, content: ByteArray) {
        // Encode and send logic similar to text
    }
}
