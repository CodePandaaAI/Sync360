package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.network.SyncBinaryChunk
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KtorSyncNetworkService : SyncNetworkService {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: Flow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isClientConnected = MutableStateFlow(false)
    override val isClientConnected: Flow<Boolean> = _isClientConnected.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<String>(extraBufferCapacity = 128)
    override val incomingPayloads: Flow<String> = _incomingPayloads.asSharedFlow()

    private val _incomingBinaryChunks = MutableSharedFlow<SyncBinaryChunk>(extraBufferCapacity = 256)
    override val incomingBinaryChunks: Flow<SyncBinaryChunk> = _incomingBinaryChunks.asSharedFlow()

    // --- Server State ---
    private var serverEngine: ApplicationEngine? = null
    // Keep track of active server WebSocket sessions by deviceId
    private val serverSessionsMutex = Mutex()
    private val activeServerSessions = mutableMapOf<String, DefaultWebSocketServerSession>()

    // --- Client State ---
    private var clientSession: DefaultClientWebSocketSession? = null
    private var clientJob: Job? = null
    private var userDisconnected = false
    private val clientSendMutex = Mutex()
    private val serverSendMutex = Mutex()
    private val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(io.ktor.client.plugins.websocket.WebSockets) {
            pingInterval = 10_000
        }
    }

    private fun updateConnectionStatus() {
        val isClient = clientSession != null
        val isServerConnected = activeServerSessions.isNotEmpty()

        _isClientConnected.value = isClient

        if (isClient || isServerConnected) {
            _connectionStatus.value = ConnectionStatus.CONNECTED
        } else if (clientJob != null && !userDisconnected) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
        } else {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }

    override fun startServer(port: Int) {
        if (serverEngine != null) return

        try {
            serverEngine = embeddedServer(io.ktor.server.cio.CIO, port = port) {
                install(io.ktor.server.websocket.WebSockets) {
                    maxFrameSize = MAX_FRAME_SIZE_BYTES
                    masking = false
                }
                routing {
                    webSocket("/sync") {
                        val deviceId = call.request.queryParameters["deviceId"] ?: "unknown"
                        println("Server: Connection accepted from deviceId=$deviceId")
                        
                        serverSessionsMutex.withLock {
                            activeServerSessions[deviceId] = this
                        }
                        updateConnectionStatus()

                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> _incomingPayloads.emit(frame.readText())
                                    is Frame.Binary -> decodeBinaryFrame(frame.readBytes())?.let {
                                        _incomingBinaryChunks.emit(it)
                                    }
                                    else -> Unit
                                }
                            }
                        } catch (e: Exception) {
                            println("Server: Session error - ${e.message}")
                        } finally {
                            println("Server: Connection closed for deviceId=$deviceId")
                            serverSessionsMutex.withLock {
                                activeServerSessions.remove(deviceId)
                            }
                            updateConnectionStatus()
                        }
                    }
                }
            }.start(wait = false)
            println("Server started on port $port")
        } catch (e: Exception) {
            println("Server: Failed to start server on port $port - ${e.message}")
            serverEngine = null
        }
    }

    override fun stopServer() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        scope.launch {
            serverSessionsMutex.withLock {
                activeServerSessions.clear()
            }
            updateConnectionStatus()
        }
        println("Server stopped")
    }

    override fun connectToPeer(host: String, port: Int, localDeviceId: String) {
        userDisconnected = true
        clientJob?.cancel()
        clientJob = null
        val previousSession = clientSession
        clientSession = null
        scope.launch {
            try {
                previousSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Reconnecting"))
            } catch (_: Exception) {}
        }
        userDisconnected = false

        _connectionStatus.value = ConnectionStatus.CONNECTING
        clientJob = scope.launch {
            while (isActive && !userDisconnected) {
                try {
                    httpClient.webSocket(host = host, port = port, path = "/sync?deviceId=$localDeviceId") {
                        println("Client: Connected to $host:$port")
                        clientSession = this
                        updateConnectionStatus()

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> _incomingPayloads.emit(frame.readText())
                                is Frame.Binary -> decodeBinaryFrame(frame.readBytes())?.let {
                                    _incomingBinaryChunks.emit(it)
                                }
                                else -> Unit
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("Client: Connection error - ${e.message}")
                } finally {
                    clientSession = null
                    updateConnectionStatus()
                }

                if (!userDisconnected) {
                    delay(3000)
                    if (isActive && !userDisconnected) {
                        updateConnectionStatus()
                    }
                }
            }
        }
    }

    override fun disconnectFromPeer() {
        userDisconnected = true
        clientJob?.cancel()
        clientJob = null
        val sessionToClose = clientSession
        clientSession = null
        scope.launch {
            try {
                sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
            } catch (_: Exception) {}

            // Disconnect all accepted server sessions too!
            val sessionsToClose = serverSessionsMutex.withLock {
                val list = activeServerSessions.values.toList()
                activeServerSessions.clear()
                list
            }
            sessionsToClose.forEach { session ->
                try {
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "User disconnected"))
                } catch (_: Exception) {}
            }
            updateConnectionStatus()
        }
    }

    override fun sendToPeer(payloadJson: String) {
        scope.launch {
            val session = clientSession
            if (session != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    clientSendMutex.withLock {
                        session.send(Frame.Text(payloadJson))
                    }
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
            val sessions = serverSessionsMutex.withLock {
                activeServerSessions.values.toList()
            }
            sessions.forEach { session ->
                try {
                    serverSendMutex.withLock {
                        session.send(Frame.Text(payloadJson))
                    }
                } catch (e: Exception) {
                    println("Server: Failed to broadcast - ${e.message}")
                }
            }
        }
    }

    override fun sendChunkToPeer(chunk: SyncBinaryChunk) {
        scope.launch {
            val session = clientSession
            if (session != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    clientSendMutex.withLock {
                        session.send(Frame.Binary(true, encodeBinaryFrame(chunk)))
                    }
                } catch (e: Exception) {
                    println("Client: Failed to send chunk - ${e.message}")
                }
            }
        }
    }

    override fun broadcastChunkToClients(chunk: SyncBinaryChunk) {
        scope.launch {
            val sessions = serverSessionsMutex.withLock { activeServerSessions.values.toList() }
            sessions.forEach { session ->
                try {
                    serverSendMutex.withLock {
                        session.send(Frame.Binary(true, encodeBinaryFrame(chunk)))
                    }
                } catch (e: Exception) {
                    println("Server: Failed to broadcast chunk - ${e.message}")
                }
            }
        }
    }

    private fun encodeBinaryFrame(chunk: SyncBinaryChunk): ByteArray {
        val header = "${chunk.offerId}|${chunk.fileIndex}|${chunk.chunkIndex}\n".encodeToByteArray()
        return header + chunk.bytes
    }

    private fun decodeBinaryFrame(bytes: ByteArray): SyncBinaryChunk? {
        val split = bytes.indexOf('\n'.code.toByte())
        if (split <= 0) return null
        val header = bytes.copyOfRange(0, split).decodeToString().split('|')
        if (header.size != 3) return null
        return SyncBinaryChunk(
            offerId = header[0],
            fileIndex = header[1].toIntOrNull() ?: return null,
            chunkIndex = header[2].toIntOrNull() ?: return null,
            bytes = bytes.copyOfRange(split + 1, bytes.size)
        )
    }

    companion object {
        private const val MAX_FRAME_SIZE_BYTES = 2L * 1024 * 1024
    }
}
