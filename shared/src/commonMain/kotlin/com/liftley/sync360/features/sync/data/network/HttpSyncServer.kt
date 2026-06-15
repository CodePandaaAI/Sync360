package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.*
import com.liftley.sync360.core.security.SessionAuthFields
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface SyncServerListener {
    fun onConnectRequest(request: ConnectRequestDto, remoteHost: String): ConnectRequestOutcome
    fun onConnectAccept(accept: ConnectAcceptDto, remoteHost: String): Boolean
    fun onConnectReject(reject: ConnectRejectDto, remoteHost: String): Boolean
    fun onTextMessage(message: MessageDto, remoteHost: String): Boolean
    fun onFileOffer(offer: FileOfferDto, remoteHost: String): Boolean
    fun onFileComplete(complete: FileCompleteDto, remoteHost: String): Boolean

    // Handlers for receiving uploaded stream chunks directly on the server
    fun onIncomingFileChunkInit(
        offerId: String,
        fileIndex: Int,
        sessionToken: String,
        authFields: SessionAuthFields,
        declaredLength: Long,
        remoteHost: String
    ): Boolean
    fun onIncomingFileChunkReceived(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean
    fun onIncomingFileChunkComplete(offerId: String, fileIndex: Int): String?
    fun onIncomingFileChunkError(
        offerId: String,
        fileIndex: Int,
        knownFailure: IncomingUploadFailure? = null
    ): IncomingUploadFailure?
    fun consumeIncomingFileFailure(offerId: String, fileIndex: Int): IncomingUploadFailure?
}

enum class ConnectRequestOutcome {
    RECEIVED,
    BUSY,
    PROTOCOL_MISMATCH,
    FORBIDDEN
}

@OptIn(ExperimentalTime::class)
class HttpSyncServer(private val port: Int = 8080) {

    private var serverEngine: ApplicationEngine? = null
    private val connectRateLimiter =
        FixedWindowRateLimiter(SyncProtocolLimits.MAX_CONNECT_REQUESTS_PER_MINUTE)
    private val controlRateLimiter =
        FixedWindowRateLimiter(SyncProtocolLimits.MAX_CONTROL_REQUESTS_PER_MINUTE)
    private val uploadRateLimiter =
        FixedWindowRateLimiter(SyncProtocolLimits.MAX_FILE_UPLOADS_PER_MINUTE)
    var listener: SyncServerListener? = null

    fun start(): Boolean {
        if (serverEngine != null) return true

        return try {
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
                        if (call.rejectRateLimited(connectRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val request = call.receive<ConnectRequestDto>()
                        val outcome = listener?.onConnectRequest(
                            request,
                            call.request.origin.remoteHost
                        )
                        call.respond(
                            when (outcome) {
                                ConnectRequestOutcome.RECEIVED -> HttpStatusCode.OK
                                ConnectRequestOutcome.BUSY -> HttpStatusCode.Conflict
                                ConnectRequestOutcome.PROTOCOL_MISMATCH ->
                                    HttpStatusCode(426, "Upgrade Required")
                                ConnectRequestOutcome.FORBIDDEN -> HttpStatusCode.Forbidden
                                null -> HttpStatusCode.ServiceUnavailable
                            }
                        )
                    }
                    
                    post(HttpSyncRoutes.ConnectAccept) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val accept = call.receive<ConnectAcceptDto>()
                        if (
                            listener?.onConnectAccept(
                                accept,
                                call.request.origin.remoteHost
                            ) == true
                        ) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.ConnectReject) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val reject = call.receive<ConnectRejectDto>()
                        if (
                            listener?.onConnectReject(
                                reject,
                                call.request.origin.remoteHost
                            ) == true
                        ) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.TextMessage) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val message = call.receive<MessageDto>()
                        if (
                            listener?.onTextMessage(
                                message,
                                call.request.origin.remoteHost
                            ) == true
                        ) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.FileOffer) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val offer = call.receive<FileOfferDto>()
                        if (
                            listener?.onFileOffer(
                                offer,
                                call.request.origin.remoteHost
                            ) == true
                        ) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.FileComplete) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val complete = call.receive<FileCompleteDto>()
                        if (
                            listener?.onFileComplete(
                                complete,
                                call.request.origin.remoteHost
                            ) == true
                        ) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.Forbidden)
                        }
                    }
                    
                    post(HttpSyncRoutes.FileUploadPattern) {
                        if (call.rejectRateLimited(uploadRateLimiter)) return@post
                        val offerId = call.parameters["offerId"]
                        val fileIndex = call.parameters["fileIndex"]?.toIntOrNull()
                        val sessionToken = call.request.headers[HttpSyncRoutes.SessionTokenHeader]
                        val issuedAt = call.request.headers[HttpSyncRoutes.IssuedAtHeader]?.toLongOrNull()
                        val nonce = call.request.headers[HttpSyncRoutes.NonceHeader]
                        val signature = call.request.headers[HttpSyncRoutes.SignatureHeader]
                        val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        val activeListener = listener
                        
                        if (
                            offerId == null ||
                            fileIndex == null ||
                            sessionToken == null ||
                            issuedAt == null ||
                            nonce == null ||
                            signature == null ||
                            declaredLength == null ||
                            offerId.length > SyncProtocolLimits.MAX_OFFER_ID_LENGTH ||
                            fileIndex !in 0 until SyncProtocolLimits.MAX_FILES_PER_TRANSFER ||
                            sessionToken.length != SyncProtocolLimits.SESSION_TOKEN_HEX_LENGTH ||
                            nonce.length != SyncProtocolLimits.NONCE_HEX_LENGTH ||
                            signature.length != SyncProtocolLimits.SIGNATURE_HEX_LENGTH ||
                            declaredLength !in 0..SyncProtocolLimits.MAX_FILE_BYTES ||
                            activeListener == null
                        ) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }

                        val channel = call.receiveChannel()
                        val buffer = ByteArray(64 * 1024)

                        try {
                            val authFields = SessionAuthFields(issuedAt, nonce, signature)
                            if (
                                !activeListener.onIncomingFileChunkInit(
                                    offerId,
                                    fileIndex,
                                    sessionToken,
                                    authFields,
                                    declaredLength,
                                    call.request.origin.remoteHost
                                )
                            ) {
                                val failure =
                                    activeListener.consumeIncomingFileFailure(offerId, fileIndex)
                                if (failure != null) {
                                    activeListener.onIncomingFileChunkError(
                                        offerId,
                                        fileIndex,
                                        failure
                                    )
                                }
                                call.respondUploadFailure(
                                    failure,
                                    HttpStatusCode.Forbidden
                                )
                                return@post
                            }

                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(buffer, 0, buffer.size)
                                if (read > 0) {
                                    val chunk = buffer.copyOf(read)
                                    val wrote = activeListener.onIncomingFileChunkReceived(offerId, fileIndex, chunk)
                                    if (!wrote) {
                                        val failure =
                                            activeListener.onIncomingFileChunkError(offerId, fileIndex)
                                        call.respondUploadFailure(
                                            failure,
                                            HttpStatusCode.InternalServerError
                                        )
                                        return@post
                                    }
                                } else if (read == -1) {
                                    break
                                }
                            }
                            
                            val savedPath = activeListener.onIncomingFileChunkComplete(offerId, fileIndex)
                            if (savedPath == null) {
                                val failure =
                                    activeListener.onIncomingFileChunkError(offerId, fileIndex)
                                call.respondUploadFailure(
                                    failure,
                                    HttpStatusCode.InternalServerError
                                )
                            } else {
                                call.respond(HttpStatusCode.OK)
                            }
                        } catch (_: Exception) {
                            val failure = activeListener.onIncomingFileChunkError(offerId, fileIndex)
                            call.respondUploadFailure(failure, HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }.start(wait = false)
            println("HttpSyncServer started on port $port")
            true
        } catch (e: Exception) {
            println("HttpSyncServer: Failed to start server on port $port - ${e.message}")
            serverEngine = null
            false
        }
    }

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        listener = null
        println("HttpSyncServer stopped")
    }
}

@OptIn(ExperimentalTime::class)
private class FixedWindowRateLimiter(
    private val maxRequests: Int
) {
    private val windows = mutableMapOf<String, RequestWindow>()

    fun allow(key: String): Boolean = synchronized(windows) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (windows.size >= MAX_TRACKED_HOSTS) {
            windows.entries.removeAll { now - it.value.startedAtMillis >= WINDOW_MILLIS }
            if (key !in windows && windows.size >= MAX_TRACKED_HOSTS) {
                return@synchronized false
            }
        }
        val current = windows[key]
        if (current == null || now - current.startedAtMillis >= WINDOW_MILLIS) {
            windows[key] = RequestWindow(now, 1)
            return@synchronized true
        }
        if (current.count >= maxRequests) return@synchronized false
        windows[key] = current.copy(count = current.count + 1)
        true
    }

    private companion object {
        const val WINDOW_MILLIS = 60_000L
        const val MAX_TRACKED_HOSTS = 1_024
    }
}

private data class RequestWindow(
    val startedAtMillis: Long,
    val count: Int
)

private suspend fun ApplicationCall.rejectRateLimited(
    limiter: FixedWindowRateLimiter
): Boolean {
    if (limiter.allow(request.origin.remoteHost)) return false
    respond(HttpStatusCode.TooManyRequests)
    return true
}

private suspend fun ApplicationCall.rejectOversizedControlBody(): Boolean {
    val declaredLength = request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: return false
    if (declaredLength in 0..SyncProtocolLimits.MAX_CONTROL_BODY_BYTES) return false
    respond(HttpStatusCode(413, "Payload Too Large"))
    return true
}

private suspend fun ApplicationCall.respondUploadFailure(
    failure: IncomingUploadFailure?,
    fallback: HttpStatusCode
) {
    respond(
        when (failure) {
            IncomingUploadFailure.STORAGE_FULL -> HttpStatusCode(507, "Insufficient Storage")
            IncomingUploadFailure.STORAGE_UNAVAILABLE -> HttpStatusCode.ServiceUnavailable
            IncomingUploadFailure.INTEGRITY -> HttpStatusCode(422, "Unprocessable Entity")
            IncomingUploadFailure.INVALID_REQUEST -> HttpStatusCode.BadRequest
            IncomingUploadFailure.WRITE_FAILED -> HttpStatusCode.InternalServerError
            null -> fallback
        }
    )
}
