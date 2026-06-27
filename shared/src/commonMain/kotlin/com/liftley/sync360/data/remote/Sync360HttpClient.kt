package com.liftley.sync360.data.remote

import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.remote.response.PingRequestResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json


class Sync360HttpClient {
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun ping(device: NearbyDevice): Result<PingRequestResponse> {
        return try {


            val host = device.hostAddresses.first()
            val port = device.port

            val url = "http://$host:$port/sync360/ping"

            Result.success(httpClient.get(url).body<PingRequestResponse>())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}