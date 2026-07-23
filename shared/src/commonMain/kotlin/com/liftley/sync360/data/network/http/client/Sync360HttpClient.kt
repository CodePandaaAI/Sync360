package com.liftley.sync360.data.network.http.client

import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.domain.model.NearbyDevice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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

    private suspend fun textOfferRequest(
        nearbyDevice: NearbyDevice,
        textOfferRequest: TextOfferRequest
    ): Result<TextOfferResponse> {
        val deviceToSendOfferPort = nearbyDevice.port

        return try {
            val textOfferResponse = requestUsingReachableAddress(nearbyDevice) { host ->
                val url = "http://${host.asUrlHost()}:$deviceToSendOfferPort/sync360/text/offer"
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(textOfferRequest)
                }.body<TextOfferResponse>()
            }

            when (textOfferResponse) {
                TextOfferResponse.Accepted -> {
                    Result.success(TextOfferResponse.Accepted)
                }

                TextOfferResponse.Declined -> {
                    Result.failure(TextOfferException("User Declined Request"))
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            when (e) {
                is ConnectTimeoutException, is SocketTimeoutException, is HttpRequestTimeoutException -> {
                    Result.failure(
                        TextOfferException(
                            e.message ?: "Device did not respond in time"
                        )
                    )
                }

                else -> Result.failure(e)
            }
        }
    }

    suspend fun textTransferRequest(
        deviceToSendOfferInfo: NearbyDevice,
        textOfferRequest: TextOfferRequest,
        textTransferRequest: TextTransferRequest
    ): Result<TextTransferResponse> {
        val result = textOfferRequest(deviceToSendOfferInfo, textOfferRequest)

        result.fold(
            onSuccess = {
                val deviceToSendOfferPort = deviceToSendOfferInfo.port

                return try {
                    val textTransferResponse = requestUsingReachableAddress(
                        deviceToSendOfferInfo
                    ) { host ->
                        val url =
                            "http://${host.asUrlHost()}:$deviceToSendOfferPort/sync360/text/transfer"
                        httpClient.post(url) {
                            contentType(ContentType.Application.Json)
                            setBody(textTransferRequest)
                        }.body<TextTransferResponse>()
                    }

                    Result.success(textTransferResponse)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    when (e) {
                        is ConnectTimeoutException, is SocketTimeoutException, is HttpRequestTimeoutException -> {
                            Result.failure(
                                FileOfferException(
                                    e.message ?: "Device did not respond in time"
                                )
                            )
                        }

                        else -> Result.failure(e)
                    }
                }
            },
            onFailure = {
                return Result.failure(it)
            }
        )
    }

    suspend fun sendFileOffer(
        device: NearbyDevice,
        fileOfferRequest: FileOfferRequest
    ): Result<FileOfferResponse> {
        val deviceToSendOfferPort = device.port

        return try {
            val fileOfferResponse = requestUsingReachableAddress(device) { host ->
                val url = "http://${host.asUrlHost()}:$deviceToSendOfferPort/sync360/file/offer"
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(fileOfferRequest)
                }.body<FileOfferResponse>()
            }

            when (fileOfferResponse) {
                FileOfferResponse.Accepted -> {
                    Result.success(FileOfferResponse.Accepted)
                }

                FileOfferResponse.Declined -> {
                    Result.failure(FileOfferException("User Declined Request"))
                }
            }
        } catch (e: Exception){
            if (e is CancellationException) throw e

            when (e) {
                is ConnectTimeoutException, is SocketTimeoutException, is HttpRequestTimeoutException -> {
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
        return "[$unwrappedHost]"
    }
}
