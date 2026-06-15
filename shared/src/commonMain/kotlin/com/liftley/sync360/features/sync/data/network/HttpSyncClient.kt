package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.*
import com.liftley.sync360.core.security.SessionAuthFields
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

    /** Binary content client: no ContentNegotiation, so file bodies stream as raw bytes. */
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
        post(ip, HttpSyncRoutes.ConnectRequest, request)

    suspend fun sendConnectAccept(ip: String, accept: ConnectAcceptDto): Boolean =
        post(ip, HttpSyncRoutes.ConnectAccept, accept)

    suspend fun sendConnectReject(ip: String, reject: ConnectRejectDto): Boolean =
        post(ip, HttpSyncRoutes.ConnectReject, reject)

    suspend fun sendTextMessage(ip: String, message: MessageDto): Boolean =
        post(ip, HttpSyncRoutes.TextMessage, message)

    suspend fun sendFileOffer(ip: String, offer: FileOfferDto): Boolean =
        post(ip, HttpSyncRoutes.FileOffer, offer)

    suspend fun sendFileComplete(ip: String, complete: FileCompleteDto): Boolean =
        post(ip, HttpSyncRoutes.FileComplete, complete)

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
        sessionToken: String,
        authFields: SessionAuthFields,
        platformOperations: com.liftley.sync360.core.platform.PlatformOperations,
        onProgress: (bytesSent: Int) -> Unit
    ): Boolean {
        return try {
            val url = buildUrl(ip, HttpSyncRoutes.fileUpload(offerId, fileIndex))
            val response = binaryHttpClient.post(url) {
                header(HttpSyncRoutes.SessionTokenHeader, sessionToken)
                header(HttpSyncRoutes.IssuedAtHeader, authFields.issuedAtMillis.toString())
                header(HttpSyncRoutes.NonceHeader, authFields.nonce)
                header(HttpSyncRoutes.SignatureHeader, authFields.signature)
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
            response.status.isSuccess()
        } catch (e: Exception) {
            println("HttpSyncClient: Upload failed for file ${file.name} to $ip - ${e.message}")
            false
        }
    }
}
