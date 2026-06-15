package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.*
import com.liftley.sync360.core.security.SessionAuthFields
import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class HttpSyncClient(private val port: Int = 8080) {
    private var isClosed = false

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        engine {
            requestTimeout = 30_000
        }
    }

    /** Binary content client: no ContentNegotiation, so file bodies stream as raw bytes. */
    private val binaryHttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = SyncProtocolLimits.FILE_IDLE_TIMEOUT_MILLIS
            connectTimeoutMillis = 10_000
        }
        engine {
            requestTimeout = 0
        }
    }

    private fun buildUrl(ip: String, targetPort: Int, path: String): String =
        "http://$ip:$targetPort$path"

    suspend fun sendConnectRequest(
        ip: String,
        targetPort: Int,
        request: ConnectRequestDto
    ): HttpTransportResult = post(ip, targetPort, HttpSyncRoutes.ConnectRequest, request)

    suspend fun sendConnectAccept(
        ip: String,
        targetPort: Int,
        accept: ConnectAcceptDto
    ): HttpTransportResult = post(ip, targetPort, HttpSyncRoutes.ConnectAccept, accept)

    suspend fun sendConnectReject(
        ip: String,
        targetPort: Int,
        reject: ConnectRejectDto
    ): HttpTransportResult = post(ip, targetPort, HttpSyncRoutes.ConnectReject, reject)

    suspend fun sendTextMessage(
        ip: String,
        targetPort: Int,
        message: MessageDto
    ): HttpTransportResult = post(ip, targetPort, HttpSyncRoutes.TextMessage, message)

    suspend fun sendFileOffer(
        ip: String,
        targetPort: Int,
        offer: FileOfferDto
    ): HttpTransportResult = post(ip, targetPort, HttpSyncRoutes.FileOffer, offer)

    suspend fun sendFileComplete(
        ip: String,
        targetPort: Int,
        complete: FileCompleteDto
    ): HttpTransportResult = post(ip, targetPort, HttpSyncRoutes.FileComplete, complete)

    private suspend inline fun <reified T> post(
        ip: String,
        targetPort: Int,
        path: String,
        body: T
    ): HttpTransportResult {
        if (isClosed) return HttpTransportResult.Failure(HttpTransportError.CLIENT_CLOSED)
        return try {
            val response = httpClient.post(buildUrl(ip, targetPort, path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            response.toTransportResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("HttpSyncClient: Error posting to $path at $ip - ${e.message}")
            e.toTransportFailure()
        }
    }

    suspend fun uploadFileChunked(
        ip: String,
        targetPort: Int,
        offerId: String,
        fileIndex: Int,
        file: com.liftley.sync360.features.sync.domain.model.PickedFile,
        sessionToken: String,
        authFields: SessionAuthFields,
        platformOperations: com.liftley.sync360.core.platform.PlatformOperations,
        onProgress: (bytesSent: Int) -> Unit
    ): HttpTransportResult {
        if (isClosed) return HttpTransportResult.Failure(HttpTransportError.CLIENT_CLOSED)
        return try {
            val url = buildUrl(ip, targetPort, HttpSyncRoutes.fileUpload(offerId, fileIndex))
            val response = binaryHttpClient.post(url) {
                header(HttpSyncRoutes.SessionTokenHeader, sessionToken)
                header(HttpSyncRoutes.IssuedAtHeader, authFields.issuedAtMillis.toString())
                header(HttpSyncRoutes.NonceHeader, authFields.nonce)
                header(HttpSyncRoutes.SignatureHeader, authFields.signature)
                setBody(object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override val contentLength = file.sizeBytes

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        val readSucceeded = platformOperations.readFileChunks(file, 1024 * 1024) { bytes ->
                            channel.writeFully(bytes)
                            onProgress(bytes.size)
                        }
                        val streamedBytes =
                            (readSucceeded as? FileOperationResult.Success<*>)?.value as? Long
                                ?: error("Could not read ${file.name}")
                        check(streamedBytes == file.sizeBytes) {
                            "File size changed while sending ${file.name}"
                        }
                    }
                })
            }
            response.toTransportResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("HttpSyncClient: Upload failed for file ${file.name} to $ip - ${e.message}")
            e.toTransportFailure()
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        httpClient.close()
        binaryHttpClient.close()
    }

    private fun HttpResponse.toTransportResult(): HttpTransportResult =
        if (status.isSuccess()) {
            HttpTransportResult.Success(status.value)
        } else {
            HttpTransportResult.Failure(
                error = when (status.value) {
                    409 -> HttpTransportError.BUSY
                    426 -> HttpTransportError.PROTOCOL_MISMATCH
                    507 -> HttpTransportError.REMOTE_STORAGE_FULL
                    503 -> HttpTransportError.REMOTE_STORAGE_UNAVAILABLE
                    422 -> HttpTransportError.INTEGRITY_FAILED
                    else -> HttpTransportError.REJECTED
                },
                statusCode = status.value
            )
        }

    private fun Exception.toTransportFailure(): HttpTransportResult.Failure {
        val typeName = this::class.simpleName.orEmpty()
        val error = when {
            this is HttpRequestTimeoutException ||
                "Timeout" in typeName -> HttpTransportError.TIMEOUT
            typeName in UNREACHABLE_EXCEPTION_NAMES -> HttpTransportError.UNREACHABLE
            this is IllegalStateException -> HttpTransportError.SOURCE_READ_FAILED
            else -> HttpTransportError.UNKNOWN
        }
        return HttpTransportResult.Failure(error = error, detail = message)
    }

    private companion object {
        val UNREACHABLE_EXCEPTION_NAMES = setOf(
            "ConnectException",
            "NoRouteToHostException",
            "UnknownHostException",
            "UnresolvedAddressException"
        )
    }
}
