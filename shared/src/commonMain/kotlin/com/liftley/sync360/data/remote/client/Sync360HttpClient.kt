package com.liftley.sync360.data.remote.client

import com.liftley.sync360.data.remote.client.clientTextRequest.TextOfferRequest
import com.liftley.sync360.data.remote.client.clientTextRequest.TextTransferRequest
import com.liftley.sync360.data.remote.server.serverTextResponse.OfferException
import com.liftley.sync360.data.remote.server.serverTextResponse.TextOfferResponse
import com.liftley.sync360.data.remote.server.serverTextResponse.TextTransferResponse
import com.liftley.sync360.domain.model.NearbyDevice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
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

                timeout {
                    requestTimeoutMillis = 60_000 // 60 seconds
                    connectTimeoutMillis = 5_000   // 5 seconds to physically connect
                    socketTimeoutMillis = 60_000  // 60 seconds socket read inactivity
                }
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
            if (e is ConnectTimeoutException || e is SocketTimeoutException) {
                // Treat the lack of response as an automatic business decline
                Result.failure(OfferException(e.message ?: ""))
            } else {
                Result.failure(e)
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