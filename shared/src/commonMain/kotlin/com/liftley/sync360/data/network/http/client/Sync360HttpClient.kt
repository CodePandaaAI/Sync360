package com.liftley.sync360.data.network.http.client

import com.liftley.sync360.data.network.http.dto.CancelRequest
import com.liftley.sync360.data.network.http.dto.CancelResponse
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryRequest
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryResponse
import com.liftley.sync360.domain.model.NearbyDevice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

class Sync360HttpClient {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 60_000
        }
    }

    suspend fun deliverText(
        targetDevice: NearbyDevice,
        request: TextDeliveryRequest
    ): Result<TextDeliveryResponse> {
        return try {
            val response = requestUsingReachableAddress(targetDevice) { host ->
                val url =
                    "http://${host.asUrlHost()}:${targetDevice.port}/sync360/text/deliver"

                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }.body<TextDeliveryResponse>()
            }

            Result.success(response)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ConnectTimeoutException) {
            Result.failure(
                TextDeliveryException("Could not connect to the device in time")
            )
        } catch (exception: SocketTimeoutException) {
            Result.failure(
                TextDeliveryException("The device did not respond in time")
            )
        } catch (exception: HttpRequestTimeoutException) {
            Result.failure(
                TextDeliveryException("The text delivery request timed out")
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun sendFileOfferRequestToDevice(
        deviceToSendFiles: NearbyDevice,
        fileOfferRequest: FileOfferRequest
    ): Result<FileOfferResponse> {
        val deviceToSendOfferPort = deviceToSendFiles.port

        return try {
            val response = requestUsingReachableAddress(deviceToSendFiles) { host ->
                val url = "http://${host.asUrlHost()}:$deviceToSendOfferPort/sync360/file/offer"
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(fileOfferRequest)
                }.body<FileOfferResponse>()
            }

            Result.success(response)
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> throw e

                is ConnectTimeoutException,
                is SocketTimeoutException,
                is HttpRequestTimeoutException -> {
                    Result.failure(
                        FileOfferException(
                            e.message ?: "Device did not respond in time"
                        )
                    )
                }

                else -> Result.failure(e)
            }
        }
    }

    suspend fun cancelOperation(
        targetDevice: NearbyDevice,
        cancelRequest: CancelRequest
    ): Result<CancelResponse> {
        return try {
            val response = requestUsingReachableAddress(targetDevice) { host ->
                val url =
                    "http://${host.asUrlHost()}:${targetDevice.port}/sync360/operation/cancel"

                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(cancelRequest)
                    timeout {
                        requestTimeoutMillis = 5_000
                    }
                }.body<CancelResponse>()
            }

            Result.success(response)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            Result.failure(exception)
        }
    }

    private suspend fun <Response> requestUsingReachableAddress(
        device: NearbyDevice,
        request: suspend (host: String) -> Response
    ): Response {
        val addresses = device.hostAddresses.distinct()
        var lastConnectionFailure: Throwable? = null

        addresses.forEachIndexed { index, host ->
            try {
                return request(host)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                val hasAnotherAddress = index < addresses.lastIndex
                if (!exception.isConnectionFailure() || !hasAnotherAddress) {
                    throw exception
                }
                lastConnectionFailure = exception
            }
        }

        throw lastConnectionFailure
            ?: IllegalArgumentException("No address is available for ${device.deviceName}")
    }

    private fun Throwable.isConnectionFailure(): Boolean {
        var current: Throwable? = this

        while (current != null) {
            if (current is ConnectTimeoutException) return true

            if (
                current::class.simpleName == "ConnectException" ||
                current::class.simpleName == "NoRouteToHostException" ||
                current::class.simpleName == "UnresolvedAddressException"
            ) {
                return true
            }

            current = current.cause
        }

        return false
    }

    private fun String.asUrlHost(): String {
        if (!contains(':')) return this

        val unwrappedHost = removePrefix("[").removeSuffix("]")
            .replace("%", "%25")
        return "[$unwrappedHost]"
    }
}
