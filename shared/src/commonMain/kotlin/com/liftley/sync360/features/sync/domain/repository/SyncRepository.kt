package com.liftley.sync360.features.sync.domain.repository

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.core.network.SyncPayload
import kotlinx.coroutines.flow.Flow

/**
 * Pure domain interface for the Sync Repository.
 * This is the central brain of the Data layer, abstracting both the database and the network sockets.
 */
interface SyncRepository {
    /** Combined stream of devices that are available nearby or actively connected via WebSocket */
    val devices: Flow<List<DeviceProfile>>
    
    /** Current status of the active connection */
    val connectionStatus: Flow<ConnectionStatus>
    
    /** The ID of the device we are currently connected to (or connecting to) */
    val activeDeviceId: Flow<String?>

    /** List of all messages/payloads received from the active connection */
    val recentPayloads: Flow<List<SyncPayload>>

    /**
     * Start discovering nearby devices on the local network.
     */
    fun startDiscovery()
    
    /**
     * Stop discovering nearby devices.
     */
    fun stopDiscovery()

    /**
     * Start the local WebSocket server to accept incoming connections.
     */
    fun startServer()
    
    /**
     * Connect to a specific device.
     */
    fun connectToDevice(device: DeviceProfile)
    
    /**
     * Disconnect the active connection and stop the server.
     */
    fun disconnectAll()

    /**
     * Send a text message to the actively connected peer(s).
     */
    fun sendText(text: String)
    
    /**
     * Send a file to the actively connected peer(s).
     */
    fun sendFile(fileName: String, mimeType: String, content: ByteArray)
    
    /**
     * Clear all synced data.
     */
    fun clearAllData()
    
    /**
     * Delete a specific device and its data.
     */
    fun deleteDevice(deviceId: String)
}
