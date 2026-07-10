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
        val deviceToSendOfferHost = nearbyDevice.hostAddresses.first()
        val deviceToSendOfferPort = nearbyDevice.port

        return try {
            val url = "http://$deviceToSendOfferHost:$deviceToSendOfferPort/sync360/text/offer"

            val textOfferResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(textOfferRequest)
            }.body<TextOfferResponse>()

            when (textOfferResponse) {
                TextOfferResponse.Accepted -> {
                    Result.success(TextOfferResponse.Accepted)
                }

                TextOfferResponse.Declined -> {
                    Result.failure(TextOfferException("User Declined Request"))
                }
            }
        } catch (e: Exception) {
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
                val deviceToSendOfferHost = deviceToSendOfferInfo.hostAddresses.first()
                val deviceToSendOfferPort = deviceToSendOfferInfo.port

                return try {
                    val url = "http://$deviceToSendOfferHost:$deviceToSendOfferPort/sync360/text/transfer"

                    val textTransferResponse = httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(textTransferRequest)
                    }.body<TextTransferResponse>()

                    Result.success(textTransferResponse)
                } catch (e: Exception) {
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

    suspend fun fileOfferRequest(
        deviceToSendOfferInfo: NearbyDevice,
        fileOfferRequest: FileOfferRequest
    ): Result<FileOfferResponse> {
        val deviceToSendOfferHost = deviceToSendOfferInfo.hostAddresses.first()
        val deviceToSendOfferPort = deviceToSendOfferInfo.port

        return try {
            val url = "http://$deviceToSendOfferHost:$deviceToSendOfferPort/sync360/file/offer"
            val fileOfferResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(fileOfferRequest)
            }.body<FileOfferResponse>()

            when (fileOfferResponse) {
                FileOfferResponse.Accepted -> {
                    Result.success(FileOfferResponse.Accepted)
                }

                FileOfferResponse.Declined -> {
                    Result.failure(FileOfferException("User Declined Request"))
                }
            }
        } catch (e: Exception){
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

    private suspend fun fileTransferRequest(
        nearbyDevice: NearbyDevice,
        fileOfferRequest: FileOfferRequest,
        fileTransferRequest: FileOfferRequest,
    ): Result<FileOfferResponse> {
        val host = nearbyDevice.hostAddresses.first()
        val port = nearbyDevice.port

        return try {
            val url = "http://$host:$port/sync360/file/transfer"
            val fileOfferResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(fileOfferRequest)
            }.body<FileOfferResponse>()

            when (fileOfferResponse) {
                FileOfferResponse.Accepted -> {
                    Result.success(FileOfferResponse.Accepted)
                }

                FileOfferResponse.Declined -> {
                    Result.failure(FileOfferException("User Declined Request"))
                }
            }
        } catch (e: Exception){
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
}