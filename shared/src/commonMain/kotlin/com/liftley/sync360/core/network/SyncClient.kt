package com.liftley.sync360.core.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus

class SyncClient {
    private val client = HttpClient {
        install(WebSockets)
    }
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()
    
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()
    
    private var session: DefaultClientWebSocketSession? = null
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var connectionJob: Job? = null
    
    fun connect(host: String, port: Int = 8080) {
        disconnect() // Clean up any existing connection job
        
        _connectionStatus.value = ConnectionStatus.CONNECTING
        connectionJob = clientScope.launch {
            while (isActive) {
                try {
                    client.webSocket(host = host, port = port, path = "/sync") {
                        session = this
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        
                        // Read incoming frames
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                _incomingMessages.emit(text)
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("SyncClient: Connection error - ${e.message}")
                } finally {
                    session = null
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
                
                // Wait before attempting reconnection
                delay(3000)
                if (isActive) {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                }
            }
        }
    }
    
    fun sendText(text: String) {
        clientScope.launch {
            val currentSession = session
            if (currentSession != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    currentSession.send(Frame.Text(text))
                } catch (e: Exception) {
                    println("SyncClient: Failed to send frame - ${e.message}")
                }
            } else {
                println("SyncClient: Cannot send text, not connected.")
            }
        }
    }

    fun sendFrame(frame: String) {
        clientScope.launch {
            val currentSession = session
            if (currentSession != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    currentSession.send(Frame.Text(frame))
                } catch (e: Exception) {
                    println("SyncClient: Failed to send frame - ${e.message}")
                }
            } else {
                println("SyncClient: Cannot send frame, not connected.")
            }
        }
    }
    
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        val currentSession = session
        session = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        
        if (currentSession != null) {
            clientScope.launch {
                try {
                    currentSession.close()
                } catch (e: Exception) {
                    // Ignore close exception
                }
            }
        }
    }

    fun close() {
        disconnect()
        clientScope.cancel()
        client.close()
    }
}
