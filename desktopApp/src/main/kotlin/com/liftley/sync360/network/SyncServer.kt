package com.liftley.sync360.network

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class SyncServer {
    private var server: NettyApplicationEngine? = null
    private val clientSessions = ConcurrentHashMap.newKeySet<DefaultWebSocketServerSession>()
    
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()
    
    private val _activeClientCount = MutableStateFlow(0)
    val activeClientCount: StateFlow<Int> = _activeClientCount.asStateFlow()
    
    private val serverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    fun start(port: Int = 8080) {
        if (server != null) return
        
        server = embeddedServer(Netty, port = port) {
            install(WebSockets)
            
            routing {
                webSocket("/sync") {
                    clientSessions.add(this)
                    _activeClientCount.value = clientSessions.size
                    println("SyncServer: New client connected. Total: ${clientSessions.size}")
                    
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                _incomingMessages.emit(text)
                                
                                // Relay this message to all OTHER connected clients
                                broadcastToOthers(text, sender = this)
                            }
                        }
                    } catch (e: Exception) {
                        println("SyncServer: Session error - ${e.message}")
                    } finally {
                        clientSessions.remove(this)
                        _activeClientCount.value = clientSessions.size
                        println("SyncServer: Client disconnected. Remaining: ${clientSessions.size}")
                    }
                }
            }
        }.start(wait = false)
        println("SyncServer: Netty server successfully initialized on port $port")
    }
    
    fun broadcast(text: String) {
        serverScope.launch {
            for (session in clientSessions) {
                try {
                    session.send(Frame.Text(text))
                } catch (e: Exception) {
                    println("SyncServer: Failed to send broadcast - ${e.message}")
                }
            }
        }
    }
    
    private suspend fun broadcastToOthers(text: String, sender: DefaultWebSocketServerSession) {
        for (session in clientSessions) {
            if (session != sender) {
                try {
                    session.send(Frame.Text(text))
                } catch (e: Exception) {
                    println("SyncServer: Failed to relay message - ${e.message}")
                }
            }
        }
    }
    
    fun stop() {
        val currentServer = server
        server = null
        if (currentServer != null) {
            try {
                currentServer.stop(1000, 2000)
                println("SyncServer: Gracefully shut down Netty server.")
            } catch (e: Exception) {
                println("SyncServer: Error shutting down server - ${e.message}")
            }
        }
        clientSessions.clear()
        _activeClientCount.value = 0
    }
}
