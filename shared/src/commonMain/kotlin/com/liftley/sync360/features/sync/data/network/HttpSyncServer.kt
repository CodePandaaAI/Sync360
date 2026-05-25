package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.debug.agentDebugLog
import com.liftley.sync360.features.sync.data.network.api.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json

interface SyncServerListener {
    fun onConnectRequest(request: ConnectRequestDto)
    fun onConnectAccept(accept: ConnectAcceptDto)
    fun onConnectReject()
    fun onTextMessage(message: MessageDto)
    fun onFileOffer(offer: FileOfferDto)
    fun onFileComplete(complete: FileCompleteDto)

    // Handlers for receiving uploaded stream chunks directly on the server
    fun onIncomingFileChunkInit(offerId: String, fileIndex: Int)
    fun onIncomingFileChunkReceived(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean
    fun onIncomingFileChunkComplete(offerId: String, fileIndex: Int): String?
    fun onIncomingFileChunkError(offerId: String, fileIndex: Int)
}

class HttpSyncServer(private val port: Int = 8080) {

    private var serverEngine: ApplicationEngine? = null
    var listener: SyncServerListener? = null

    fun start() {
        if (serverEngine != null) return

        try {
            serverEngine = embeddedServer(CIO, port = port, configure = {
                connectionIdleTimeoutSeconds = 3600
            }) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                
                routing {
                    post("/api/connect/request") {
                        val request = call.receive<ConnectRequestDto>()
                        listener?.onConnectRequest(request)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/connect/accept") {
                        val accept = call.receive<ConnectAcceptDto>()
                        listener?.onConnectAccept(accept)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/connect/reject") {
                        listener?.onConnectReject()
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/message/text") {
                        val message = call.receive<MessageDto>()
                        listener?.onTextMessage(message)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/file/offer") {
                        val offer = call.receive<FileOfferDto>()
                        listener?.onFileOffer(offer)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/file/complete") {
                        val complete = call.receive<FileCompleteDto>()
                        listener?.onFileComplete(complete)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/file/upload/{offerId}/{fileIndex}") {
                        val offerId = call.parameters["offerId"]
                        val fileIndex = call.parameters["fileIndex"]?.toIntOrNull()
                        val activeListener = listener
                        // #region agent log
                        agentDebugLog(
                            location = "HttpSyncServer.kt:POST/api/file/upload",
                            message = "file upload stream init",
                            hypothesisId = "B",
                            data = mapOf(
                                "offerId" to (offerId ?: "null"),
                                "fileIndex" to (fileIndex?.toString() ?: "null"),
                                "hasListener" to (activeListener != null).toString()
                            )
                        )
                        // #endregion
                        
                        if (offerId == null || fileIndex == null || activeListener == null) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }

                        // Read the incoming binary stream from Ktor's ByteReadChannel
                        val channel = call.receiveChannel()
                        val buffer = ByteArray(1024 * 1024)
                        var totalRead = 0L

                        try {
                            activeListener.onIncomingFileChunkInit(offerId, fileIndex)

                            while (!channel.isClosedForRead) {
                                if (channel.availableForRead == 0) {
                                    channel.awaitContent()
                                    if (channel.availableForRead == 0 && channel.isClosedForRead) break
                                    continue
                                }
                                val toRead = minOf(channel.availableForRead.toInt(), buffer.size)
                                val read = channel.readAvailable(buffer, 0, toRead)
                                if (read > 0) {
                                    val chunk = buffer.copyOf(read)
                                    val wrote = activeListener.onIncomingFileChunkReceived(offerId, fileIndex, chunk)
                                    if (!wrote) {
                                        // #region agent log
                                        agentDebugLog(
                                            location = "HttpSyncServer.kt:POST/api/file/upload",
                                            message = "file chunk write failed",
                                            hypothesisId = "D",
                                            data = mapOf("offerId" to offerId, "fileIndex" to fileIndex.toString())
                                        )
                                        // #endregion
                                        call.respond(HttpStatusCode.InternalServerError)
                                        return@post
                                    }
                                    totalRead += read
                                } else if (read == -1) {
                                    break
                                }
                            }
                            
                            val savedPath = activeListener.onIncomingFileChunkComplete(offerId, fileIndex)
                            // #region agent log
                            agentDebugLog(
                                location = "HttpSyncServer.kt:POST/api/file/upload",
                                message = "file upload stream complete",
                                hypothesisId = "C",
                                data = mapOf(
                                    "offerId" to offerId,
                                    "fileIndex" to fileIndex.toString(),
                                    "totalRead" to totalRead.toString(),
                                    "savedPath" to (savedPath ?: "null")
                                )
                            )
                            // #endregion
                            if (savedPath == null) {
                                call.respond(HttpStatusCode.InternalServerError)
                            } else {
                                call.respond(HttpStatusCode.OK)
                            }
                        } catch (e: Exception) {
                            // #region agent log
                            agentDebugLog(
                                location = "HttpSyncServer.kt:POST/api/file/upload",
                                message = "file upload stream error",
                                hypothesisId = "E",
                                data = mapOf(
                                    "offerId" to offerId,
                                    "fileIndex" to fileIndex.toString(),
                                    "error" to (e.message ?: e::class.simpleName.orEmpty())
                                )
                            )
                            // #endregion
                            activeListener.onIncomingFileChunkError(offerId, fileIndex)
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }.start(wait = false)
            println("HttpSyncServer started on port $port")
        } catch (e: Exception) {
            println("HttpSyncServer: Failed to start server on port $port - ${e.message}")
            serverEngine = null
        }
    }

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        println("HttpSyncServer stopped")
    }
}
