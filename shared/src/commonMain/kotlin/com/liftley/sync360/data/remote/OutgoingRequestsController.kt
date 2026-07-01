package com.liftley.sync360.data.remote

import com.liftley.sync360.data.remote.client.Sync360HttpClient
import com.liftley.sync360.data.remote.client.clientTextRequest.TextOfferRequest
import com.liftley.sync360.data.remote.client.clientTextRequest.TextTransferRequest
import com.liftley.sync360.data.remote.server.serverTextResponse.TextTransferResponse
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.NearbyDevice

class OutgoingRequestsController(
    private val httpClient: Sync360HttpClient,
    private val localDeviceInfoProvider: LocalDeviceInfoProvider
) {

    suspend fun offerRequestToPeer(
        device: NearbyDevice,
        text: String
    ): Result<TextTransferResponse> {
        val localDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()
        val textOfferRequest = TextOfferRequest(
            senderDeviceId = localDeviceInfo.deviceId,
            senderDeviceName = localDeviceInfo.deviceName,
            preview = text.take(10),
            characterCount = text.count()
        )

        val textTransferRequest = TextTransferRequest(
            text = text
        )

        return httpClient.textTransferRequest(
            device,
            textOfferRequest = textOfferRequest,
            textTransferRequest = textTransferRequest
        )
    }
}