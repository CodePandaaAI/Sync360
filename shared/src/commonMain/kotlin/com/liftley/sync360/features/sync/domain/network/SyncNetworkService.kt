package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Pure domain interface for the WebSocket networking service.
 * Represents symmetric capabilities: it can start a local server and connect as a client to a remote server.
 */
interface SyncNetworkService {
    /** Flow of the current connection status to a remote peer (Client side) */
    val connectionStatus: Flow<ConnectionStatus>
    
    /** Flow to track specifically whether the client-side socket is connected. */
    val isClientConnected: Flow<Boolean>
    
    /** Flow of raw JSON payloads received from peers (both Client and Server sides) */
    val incomingPayloads: Flow<String>

    /**
     * Start the local embedded server to accept incoming connections.
     * @param port The port to listen on.
     */
    fun startServer(port: Int = 8080)
    
    /**
     * Stop the local embedded server.
     */
    fun stopServer()

    /**
     * Connect to a remote peer as a client.
     * @param host The IP address of the target.
     * @param port The port of the target.
     * @param localDeviceId Our device ID to identify ourselves.
     */
    fun connectToPeer(host: String, port: Int = 8080, localDeviceId: String)
    
    /**
     * Disconnect the active client connection.
     */
    fun disconnectFromPeer()

    /**
     * Send a JSON payload to the currently connected remote peer via the client socket.
     */
    fun sendToPeer(payloadJson: String)
    
    /**
     * Broadcast a JSON payload to all connected clients via the server socket.
     */
    fun broadcastToClients(payloadJson: String)
}
