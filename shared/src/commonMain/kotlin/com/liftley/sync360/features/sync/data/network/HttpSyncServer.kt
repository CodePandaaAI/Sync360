package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.*
import com.liftley.sync360.core.security.SessionAuthFields
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
    fun onConnectReject(reject: ConnectRejectDto): Boolean
    fun onTextMessage(message: MessageDto): Boolean
    fun onFileOffer(offer: FileOfferDto): Boolean
    fun onFileComplete(complete: FileCompleteDto): Boolean

    // Handlers for receiving uploaded stream chunks directly on the server
    fun onIncomingFileChunkInit(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields
    ): Boolean
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
                    post(HttpSyncRoutes.ConnectRequest) {
                        val request = call.receive<ConnectRequestDto>()
                        listener?.onConnectRequest(request)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post(HttpSyncRoutes.ConnectAccept) {
                        val accept = call.receive<ConnectAcceptDto>()
                        listener?.onConnectAccept(accept)
                        call.respond(HttpStatusCode.OK)
                    }
                    
                    post(HttpSyncRoutes.ConnectReject) {
                        val reject = call.receive<ConnectRejectDto>()
                        if (listener?.onConnectReject(reject) == true) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.TextMessage) {
                        val message = call.receive<MessageDto>()
                        if (listener?.onTextMessage(message) == true) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.FileOffer) {
                        val offer = call.receive<FileOfferDto>()
                        if (listener?.onFileOffer(offer) == true) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.FileComplete) {
                        val complete = call.receive<FileCompleteDto>()
                        if (listener?.onFileComplete(complete) == true) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.FileUploadPattern) {
                        val offerId = call.parameters["offerId"]
                        val fileIndex = call.parameters["fileIndex"]?.toIntOrNull()
                        val sessionToken = call.request.headers[HttpSyncRoutes.SessionTokenHeader]
                        val issuedAt = call.request.headers[HttpSyncRoutes.IssuedAtHeader]?.toLongOrNull()
                        val nonce = call.request.headers[HttpSyncRoutes.NonceHeader]
                        val signature = call.request.headers[HttpSyncRoutes.SignatureHeader]
                        val activeListener = listener
                        
                        if (
                            offerId == null ||
                            fileIndex == null ||
                            sessionToken == null ||
                            issuedAt == null ||
                            nonce == null ||
                            signature == null ||
                            activeListener == null
                        ) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }

                        val channel = call.receiveChannel()
                        val buffer = ByteArray(1024 * 1024)

                        try {
                            val authFields = SessionAuthFields(issuedAt, nonce, signature)
                            if (!activeListener.onIncomingFileChunkInit(offerId, fileIndex, sessionToken, authFields)) {
                                call.respond(HttpStatusCode.Forbidden)
                                return@post
                            }

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
                                        call.respond(HttpStatusCode.InternalServerError)
                                        return@post
                                    }
                                } else if (read == -1) {
                                    break
                                }
                            }
                            
                            val savedPath = activeListener.onIncomingFileChunkComplete(offerId, fileIndex)
                            if (savedPath == null) {
                                call.respond(HttpStatusCode.InternalServerError)
                            } else {
                                call.respond(HttpStatusCode.OK)
                            }
                        } catch (_: Exception) {
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
