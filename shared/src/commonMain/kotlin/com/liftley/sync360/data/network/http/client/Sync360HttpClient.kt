package com.liftley.sync360.data.network.http.client

import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.data.remote.server.serverTextResponse.OfferException
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
        val host = nearbyDevice.hostAddresses.first()
        val port = nearbyDevice.port

        return try {
            val url = "http://$host:$port/sync360/text/offer"

            val textOfferResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(textOfferRequest)
            }.body<TextOfferResponse>()

            when (textOfferResponse) {
                TextOfferResponse.Accepted -> {
                    Result.success(TextOfferResponse.Accepted)
                }

                TextOfferResponse.Declined -> {
                    Result.failure(OfferException("User Declined Request"))
                }
            }
        } catch (e: Exception) {
            when (e) {
                is ConnectTimeoutException, is SocketTimeoutException, is HttpRequestTimeoutException -> {
                    Result.failure(OfferException(e.message ?: "Device did not respond in time"))
                }

                else -> Result.failure(e)
            }
        }
    }

    suspend fun textTransferRequest(
        nearbyDevice: NearbyDevice,
        textOfferRequest: TextOfferRequest,
        textTransferRequest: TextTransferRequest
    ): Result<TextTransferResponse> {
        val result = textOfferRequest(nearbyDevice, textOfferRequest)

        result.fold(
            onSuccess = {
                val host = nearbyDevice.hostAddresses.first()
                val port = nearbyDevice.port

                return try {
                    val url = "http://$host:$port/sync360/text/transfer"

                    val textTransferResponse = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(textTransferRequest)
                    }.body<TextTransferResponse>()

                    Result.success(textTransferResponse)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = {
                return Result.failure(it)
            }
        )
    }
}