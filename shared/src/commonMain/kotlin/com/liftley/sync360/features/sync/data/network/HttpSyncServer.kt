package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferResponseDto
import com.liftley.sync360.features.sync.data.network.api.HttpSyncRoutes
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

interface SyncServerListener {
    suspend fun onFileOffer(offer: FileOfferDto, remoteHost: String): FileOfferResponseDto
    fun onFileComplete(complete: FileCompleteDto, remoteHost: String): Boolean
}

@OptIn(ExperimentalTime::class)
class HttpSyncServer(private val port: Int = 8080) {
    private var serverEngine: ApplicationEngine? = null
    private val connectRateLimiter =
        FixedWindowRateLimiter(SyncProtocolLimits.MAX_CONNECT_REQUESTS_PER_MINUTE)
    private val controlRateLimiter =
        FixedWindowRateLimiter(SyncProtocolLimits.MAX_CONTROL_REQUESTS_PER_MINUTE)
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


                    post(HttpSyncRoutes.FileOffer) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val response = listener?.onFileOffer(
                            call.receive<FileOfferDto>(),
                            call.request.origin.remoteHost
                        ) ?: FileOfferResponseDto(
                            accepted = false,
                            failureReason = "raw_tcp_receiver_unavailable"
                        )
                        call.respond(
                            if (response.accepted) HttpStatusCode.OK else HttpStatusCode.Forbidden,
                            response
                        )
                    }

                    post(HttpSyncRoutes.FileComplete) {
                        if (call.rejectRateLimited(controlRateLimiter)) return@post
                        if (call.rejectOversizedControlBody()) return@post
                        val accepted = listener?.onFileComplete(
                            call.receive<FileCompleteDto>(),
                            call.request.origin.remoteHost
                        ) == true
                        call.respond(if (accepted) HttpStatusCode.OK else HttpStatusCode.Forbidden)
                    }
                }
            }.start(wait = false)
            println("HttpSyncServer started on port $port")
            true
        } catch (error: Exception) {
            println("HttpSyncServer: Failed to start server on port $port - ${error.message}")
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
private class FixedWindowRateLimiter(private val maxRequests: Int) {
    private val windows = mutableMapOf<String, RequestWindow>()

    fun allow(key: String): Boolean = synchronized(windows) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (windows.size >= MAX_TRACKED_HOSTS) {
            windows.entries.removeAll { now - it.value.startedAtMillis >= WINDOW_MILLIS }
            if (key !in windows && windows.size >= MAX_TRACKED_HOSTS) return@synchronized false
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

private data class RequestWindow(val startedAtMillis: Long, val count: Int)

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
