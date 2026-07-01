package com.liftley.sync360.data.remote

import com.liftley.sync360.data.remote.client.Sync360HttpClient
import com.liftley.sync360.data.remote.client.clientRequest.MessagePayload
import com.liftley.sync360.data.remote.client.clientRequest.OfferRequest
import com.liftley.sync360.data.remote.server.serverResponse.BaseResponse
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.presentation.featureSend.model.SendItem

class OutgoingRequestsController(private val httpClient: Sync360HttpClient) {

    suspend fun offerRequestToPeer(device: NearbyDevice, data: SendItem.Text): Result<BaseResponse> {
        val offerRequest = OfferRequest(
            deviceName = device.deviceName,
            deviceIp = device.hostAddresses.first(),
            devicePort = device.port,
            deviceId = device.id,
            filesCount = 1
        )
        return httpClient.trySendPayloadToPeer(device, offerRequest, MessagePayload(data.text))
    }
}