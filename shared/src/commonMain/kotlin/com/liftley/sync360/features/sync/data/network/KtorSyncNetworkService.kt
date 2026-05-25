package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

class KtorSyncNetworkService : SyncNetworkService {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: Flow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val incomingPayloads: Flow<String> = _incomingPayloads.asSharedFlow()

    // --- Server State ---
    private var serverEngine: ApplicationEngine? = null
    // Keep track of active server WebSocket sessions to broadcast to them
    private val activeServerSessions = mutableSetOf<DefaultWebSocketServerSession>()

    // --- Client State ---
    private var clientSession: DefaultClientWebSocketSession? = null
    private var clientJob: Job? = null
    private val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.websocket.WebSockets) {
            pingInterval = 10_000
        }
    }

    override fun startServer(port: Int) {
        if (serverEngine != null) return

        serverEngine = embeddedServer(io.ktor.server.cio.CIO, port = port) {
            install(io.ktor.server.websocket.WebSockets) {
                // Removed pingPeriod and timeout due to KMP resolution issues with Java Duration
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            routing {
                webSocket("/sync") {
                    val deviceId = call.request.queryParameters["deviceId"] ?: "unknown"
                    println("Server: Connection accepted from deviceId=$deviceId")
                    activeServerSessions.add(this)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                _incomingPayloads.emit(frame.readText())
                            }
                        }
                    } catch (e: Exception) {
                        println("Server: Session error - ${e.message}")
                    } finally {
                        println("Server: Connection closed for deviceId=$deviceId")
                        activeServerSessions.remove(this)
                    }
                }
            }
        }.start(wait = false)
        println("Server started on port $port")
    }

    override fun stopServer() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        activeServerSessions.clear()
        println("Server stopped")
    }

    override fun connectToPeer(host: String, port: Int, localDeviceId: String) {
        disconnectFromPeer()

        _connectionStatus.value = ConnectionStatus.CONNECTING
        clientJob = scope.launch {
            while (isActive) {
                try {
                    httpClient.webSocket(host = host, port = port, path = "/sync?deviceId=$localDeviceId") {
                        println("Client: Connected to $host:$port")
                        clientSession = this
                        _connectionStatus.value = ConnectionStatus.CONNECTED

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                _incomingPayloads.emit(frame.readText())
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Client: Connection error - ${e.message}")
                } finally {
                    clientSession = null
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }

                // Automatic reconnect backoff
                delay(3000)
                if (isActive) {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                }
            }
        }
    }

    override fun disconnectFromPeer() {
        clientJob?.cancel()
        clientJob = null
        scope.launch {
            clientSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            clientSession = null
        }
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    override fun sendToPeer(payloadJson: String) {
        scope.launch {
            val session = clientSession
            if (session != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    session.send(Frame.Text(payloadJson))
                } catch (e: Exception) {
                    println("Client: Failed to send - ${e.message}")
                }
            } else {
                println("Client: Cannot send, not connected to peer.")
            }
        }
    }

    override fun broadcastToClients(payloadJson: String) {
        scope.launch {
            val sessions = activeServerSessions.toList()
            sessions.forEach { session ->
                try {
                    session.send(Frame.Text(payloadJson))
                } catch (e: Exception) {
                    println("Server: Failed to broadcast - ${e.message}")
                }
            }
        }
    }
}
