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
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
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

    /** Binary content client - no ContentNegotiation to prevent body buffering. */
    private val binaryHttpClient = HttpClient(CIO) {
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

    suspend fun uploadFileChunked(
        ip: String,
        offerId: String,
        fileIndex: Int,
        file: com.liftley.sync360.features.sync.domain.model.PickedFile,
        platformOperations: com.liftley.sync360.core.platform.PlatformOperations,
        onProgress: (bytesSent: Int) -> Unit
    ): Boolean {
        // #region agent log
        agentDebugLog(
            location = "HttpSyncClient.kt:uploadFileChunked",
            message = "upload start",
            hypothesisId = "A",
            data = mapOf("fileName" to file.name, "fileSize" to file.sizeBytes.toString())
        )
        // #endregion
        return try {
            val url = buildUrl(ip, "/api/file/upload/$offerId/$fileIndex")
            val response = binaryHttpClient.post(url) {
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override val contentLength = file.sizeBytes

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        platformOperations.readFileChunks(file, 1024 * 1024) { bytes ->
                            channel.writeFully(bytes)
                            onProgress(bytes.size)
                        }
                    }
                })
            }
            // #region agent log
            agentDebugLog(
                location = "HttpSyncClient.kt:uploadFileChunked",
                message = "upload finished",
                hypothesisId = "A",
                data = mapOf("fileName" to file.name, "status" to response.status.value.toString())
            )
            // #endregion
            response.status.isSuccess()
        } catch (e: Exception) {
            // #region agent log
            agentDebugLog(
                location = "HttpSyncClient.kt:uploadFileChunked",
                message = "upload exception",
                hypothesisId = "A",
                data = mapOf("fileName" to file.name, "error" to (e.message ?: e::class.simpleName.orEmpty()))
            )
            // #endregion
            println("HttpSyncClient: Upload failed for file ${file.name} to $ip - ${e.message}")
            false
        }
    }
}
