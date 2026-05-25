package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.debug.agentDebugLog
import com.liftley.sync360.features.sync.data.network.api.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class HttpSyncClient(private val port: Int = 8080) {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30_000
        }
        engine {
            requestTimeout = 0
        }
    }

    /** Raw-byte downloads only — no ContentNegotiation so bodies are not buffered as JSON. */
    private val downloadHttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30_000
        }
        engine {
            requestTimeout = 0
        }
    }

    private fun buildUrl(ip: String, path: String): String = "http://$ip:$port$path"

    suspend fun sendConnectRequest(ip: String, request: ConnectRequestDto): Boolean =
        post(ip, "/api/connect/request", request)

    suspend fun sendConnectAccept(ip: String, accept: ConnectAcceptDto): Boolean =
        post(ip, "/api/connect/accept", accept)

    suspend fun sendConnectReject(ip: String): Boolean =
        post(ip, "/api/connect/reject", "") // Empty body

    suspend fun sendTextMessage(ip: String, message: MessageDto): Boolean =
        post(ip, "/api/message/text", message)

    suspend fun sendFileOffer(ip: String, offer: FileOfferDto): Boolean =
        post(ip, "/api/file/offer", offer)

    suspend fun sendFileAccept(ip: String, accept: FileAcceptDto): Boolean =
        post(ip, "/api/file/accept", accept)

    suspend fun sendFileReject(ip: String, reject: FileRejectDto): Boolean =
        post(ip, "/api/file/reject", reject)

    suspend fun sendFileComplete(ip: String, complete: FileCompleteDto): Boolean =
        post(ip, "/api/file/complete", complete)

    private suspend inline fun <reified T> post(ip: String, path: String, body: T): Boolean {
        return try {
            val response = httpClient.post(buildUrl(ip, path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            println("HttpSyncClient: Error posting to $path at $ip - ${e.message}")
            false
        }
    }

    suspend fun downloadFileChunked(
        url: String,
        onChunk: suspend (ByteArray) -> Boolean
    ): Boolean {
        // #region agent log
        agentDebugLog(
            location = "HttpSyncClient.kt:downloadFileChunked",
            message = "download start",
            hypothesisId = "A",
            data = mapOf("url" to url),
            runId = "post-fix-2"
        )
        // #endregion
        return try {
            var totalRead = 0L
            var chunkCount = 0
            val buffer = ByteArray(256 * 1024)
            val ok = downloadHttpClient.prepareGet(url).execute { response ->
                // #region agent log
                agentDebugLog(
                    location = "HttpSyncClient.kt:downloadFileChunked",
                    message = "download response",
                    hypothesisId = "A",
                    data = mapOf(
                        "url" to url,
                        "status" to response.status.value.toString(),
                        "contentLength" to (response.contentLength()?.toString() ?: "unknown")
                    ),
                    runId = "post-fix-2"
                )
                // #endregion
                if (!response.status.isSuccess()) return@execute false
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    if (channel.availableForRead == 0) {
                        channel.awaitContent()
                        if (channel.availableForRead == 0 && channel.isClosedForRead) break
                        continue
                    }
                    val toRead = minOf(channel.availableForRead.toInt(), buffer.size)
                    val read = channel.readAvailable(buffer, 0, toRead)
                    when {
                        read > 0 -> {
                            chunkCount++
                            totalRead += read
                            val chunk = buffer.copyOf(read)
                            if (!onChunk(chunk)) {
                                // #region agent log
                                agentDebugLog(
                                    location = "HttpSyncClient.kt:downloadFileChunked",
                                    message = "onChunk returned false",
                                    hypothesisId = "D",
                                    data = mapOf(
                                        "url" to url,
                                        "totalRead" to totalRead.toString(),
                                        "chunkCount" to chunkCount.toString()
                                    ),
                                    runId = "post-fix-2"
                                )
                                // #endregion
                                return@execute false
                            }
                        }
                        read == -1 -> break
                    }
                }
                true
            }
            // #region agent log
            agentDebugLog(
                location = "HttpSyncClient.kt:downloadFileChunked",
                message = "download finished",
                hypothesisId = "A",
                data = mapOf(
                    "url" to url,
                    "ok" to ok.toString(),
                    "totalRead" to totalRead.toString(),
                    "chunkCount" to chunkCount.toString()
                ),
                runId = "post-fix-2"
            )
            // #endregion
            ok
        } catch (e: Exception) {
            // #region agent log
            agentDebugLog(
                location = "HttpSyncClient.kt:downloadFileChunked",
                message = "download exception",
                hypothesisId = "A",
                data = mapOf(
                    "url" to url,
                    "error" to (e.message ?: e::class.simpleName.orEmpty())
                ),
                runId = "post-fix-2"
            )
            // #endregion
            println("HttpSyncClient: Download failed for $url - ${e.message}")
            false
        }
    }
}
