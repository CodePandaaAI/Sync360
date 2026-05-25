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
import kotlinx.serialization.json.Json

interface SyncServerListener {
    fun onConnectRequest(request: ConnectRequestDto)
    fun onConnectAccept(accept: ConnectAcceptDto)
    fun onConnectReject()
    fun onTextMessage(message: MessageDto)
    fun onFileOffer(offer: FileOfferDto)
    fun onFileAccept(accept: FileAcceptDto)
    fun onFileReject(reject: FileRejectDto)
    fun onFileComplete(complete: FileCompleteDto)
    
    fun getOutgoingFileSize(offerId: String, fileIndex: Int): Long?

    // Returns true if chunk was written successfully, false otherwise
    suspend fun serveFileChunk(offerId: String, fileIndex: Int, chunkSizeBytes: Int, onChunk: suspend (ByteArray) -> Unit)
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
                    
                    post("/api/file/accept") {
                        val accept = call.receive<FileAcceptDto>()
                        listener?.onFileAccept(accept)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/file/reject") {
                        val reject = call.receive<FileRejectDto>()
                        listener?.onFileReject(reject)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post("/api/file/complete") {
                        val complete = call.receive<FileCompleteDto>()
                        listener?.onFileComplete(complete)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    get("/files/{offerId}/{fileIndex}") {
                        val offerId = call.parameters["offerId"]
                        val fileIndex = call.parameters["fileIndex"]?.toIntOrNull()
                        val activeListener = listener
                        // #region agent log
                        agentDebugLog(
                            location = "HttpSyncServer.kt:GET/files",
                            message = "file download request",
                            hypothesisId = "B",
                            data = mapOf(
                                "offerId" to (offerId ?: "null"),
                                "fileIndex" to (fileIndex?.toString() ?: "null"),
                                "hasListener" to (activeListener != null).toString()
                            )
                        )
                        // #endregion
                        
                        if (offerId == null || fileIndex == null || activeListener == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@get
                        }

                        val fileSize = activeListener.getOutgoingFileSize(offerId, fileIndex)
                        if (fileSize != null && fileSize > 0) {
                            call.response.header(HttpHeaders.ContentLength, fileSize.toString())
                        }
                        
                        var bytesWritten = 0L
                        call.respondOutputStream(ContentType.Application.OctetStream) {
                            activeListener.serveFileChunk(offerId, fileIndex, 256 * 1024) { bytes ->
                                write(bytes)
                                flush()
                                bytesWritten += bytes.size
                            }
                            // #region agent log
                            agentDebugLog(
                                location = "HttpSyncServer.kt:GET/files",
                                message = "file download stream finished",
                                hypothesisId = "E",
                                data = mapOf(
                                    "offerId" to offerId,
                                    "fileIndex" to fileIndex.toString(),
                                    "bytesWritten" to bytesWritten.toString()
                                )
                            )
                            // #endregion
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
