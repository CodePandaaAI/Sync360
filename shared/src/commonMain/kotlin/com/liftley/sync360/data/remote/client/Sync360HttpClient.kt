package com.liftley.sync360.data.remote.client

import com.liftley.sync360.data.remote.client.clientRequest.MessagePayload
import com.liftley.sync360.data.remote.client.clientRequest.OfferRequest
import com.liftley.sync360.data.remote.server.serverResponse.BaseResponse
import com.liftley.sync360.data.remote.server.serverResponse.OfferException
import com.liftley.sync360.data.remote.server.serverResponse.OfferResponse
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

    private suspend fun offerRequest(
        nearbyDevice: NearbyDevice,
        offerRequest: OfferRequest
    ): Result<OfferResponse> {
        val host = nearbyDevice.hostAddresses.first()
        val port = nearbyDevice.port

        return try {
            val url = "http://$host:$port/sync360/offer"
            when (val offerResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(offerRequest)

                timeout {
                    requestTimeoutMillis = 60_000 // 60 seconds
                    connectTimeoutMillis = 5_000   // 5 seconds to physically connect
                    socketTimeoutMillis = 60_000  // 60 seconds socket read inactivity
                }
            }.body<OfferResponse>()) {
                OfferResponse.OfferAccepted -> {
                    Result.success(offerResponse)
                }

                OfferResponse.OfferDeclined -> {
                    Result.failure(OfferException(offerResponse))
                }
            }
        } catch (e: Exception) {
            if (e is ConnectTimeoutException || e is SocketTimeoutException) {
                // Treat the lack of response as an automatic business decline
                Result.failure(OfferException(OfferResponse.OfferDeclined))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun trySendPayloadToPeer(
        nearbyDevice: NearbyDevice,
        offerRequest: OfferRequest,
        messagePayload: MessagePayload
    ): Result<BaseResponse> {
        val result = offerRequest(nearbyDevice, offerRequest)

        result.fold(
            onSuccess = {
                val host = nearbyDevice.hostAddresses.first()
                val port = nearbyDevice.port

                return try {
                    val url = "http://$host:$port/sync360/files"

                    val response = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(messagePayload)
                    }.body<BaseResponse>()

                    Result.success(response)
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