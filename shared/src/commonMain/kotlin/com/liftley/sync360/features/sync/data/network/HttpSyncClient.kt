package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.data.network.api.FileCompleteDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferDto
import com.liftley.sync360.features.sync.data.network.api.FileOfferResponseDto
import com.liftley.sync360.features.sync.data.network.api.HttpSyncRoutes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
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

    private fun buildUrl(ip: String, targetPort: Int, path: String): String =
        "http://$ip:$targetPort$path"



    suspend fun sendFileOffer(
        ip: String,
        targetPort: Int,
        offer: FileOfferDto
    ): FileOfferTransportResult {
        if (isClosed) {
            return FileOfferTransportResult.Failure(
                HttpTransportResult.Failure(HttpTransportError.CLIENT_CLOSED)
            )
        }
        return try {
            val response = httpClient.post(buildUrl(ip, targetPort, HttpSyncRoutes.FileOffer)) {
                contentType(ContentType.Application.Json)
                setBody(offer)
            }
            if (!response.status.isSuccess()) {
                val rejection = runCatching {
                    response.body<FileOfferResponseDto>()
                }.getOrNull()
                FileOfferTransportResult.Failure(
                    (response.toTransportResult() as HttpTransportResult.Failure).copy(
                        detail = rejection?.failureReason
                    )
                )
            } else {
                val body = response.body<FileOfferResponseDto>()
                if (
                    body.accepted &&
                    body.rawTcpHost != null &&
                    body.rawTcpPort != null &&
                    body.rawTcpPort in 1..65_535 &&
                    body.transferId == offer.offerId &&
                    body.transferToken != null
                ) {
                    FileOfferTransportResult.Accepted(body)
                } else {
                    FileOfferTransportResult.Failure(
                        HttpTransportResult.Failure(
                            error = HttpTransportError.REJECTED,
                            statusCode = response.status.value,
                            detail = body.failureReason ?: "Invalid raw TCP offer response"
                        )
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FileOfferTransportResult.Failure(error.toTransportFailure())
        }
    }

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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("HttpSyncClient: Error posting to $path at $ip - ${error.message}")
            error.toTransportFailure()
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        httpClient.close()
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
            this is HttpRequestTimeoutException || "Timeout" in typeName ->
                HttpTransportError.TIMEOUT
            typeName in UNREACHABLE_EXCEPTION_NAMES -> HttpTransportError.UNREACHABLE
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
