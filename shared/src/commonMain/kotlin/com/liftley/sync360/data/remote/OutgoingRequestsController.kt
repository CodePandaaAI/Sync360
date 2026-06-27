package com.liftley.sync360.data.remote

import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.remote.response.PingRequestResponse

class OutgoingRequestsController(private val httpClient: Sync360HttpClient) {
    suspend fun sendPingRequestToDevice(device: NearbyDevice): PingRequestResponse {
        return httpClient.ping(device).fold(
            onSuccess = {
                it
            },
            onFailure = {
                PingRequestResponse.Declined(it.message ?: "Cant get Result")
            }
        )
    }
}