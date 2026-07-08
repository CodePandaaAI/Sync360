package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.client.Sync360HttpClient
import com.liftley.sync360.data.network.http.dto.file.FileOfferItem
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferResponse
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.presentation.send.model.PickedFile

class OutgoingRequestsController(
    private val httpClient: Sync360HttpClient,
    private val localDeviceInfoProvider: LocalDeviceInfoProvider
) {

    suspend fun sendTextOffer(
        device: NearbyDevice,
        text: String
    ): Result<TextTransferResponse> {
        val localDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val textOfferRequest = TextOfferRequest(
            senderDeviceId = localDeviceInfo.deviceId,
            senderDeviceName = localDeviceInfo.deviceName,
            preview = text.take(180),
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

    suspend fun sendFileOffer(
        device: NearbyDevice,
        files: List<PickedFile>
    ): Result<FileOfferResponse> {
        val localDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val totalSizeBytes = if (files.all { it.sizeBytes != null }) {
            files.sumOf { it.sizeBytes ?: 0L }
        } else {
            null
        }

        val fileOfferItems = files.map {
            FileOfferItem(
                fileName = it.displayName,
                fileSizeBytes = it.sizeBytes,
                mimeType = it.mimeType
            )
        }

        val fileOfferRequest = FileOfferRequest(
            senderDeviceId = localDeviceInfo.deviceId,
            senderDeviceName = localDeviceInfo.deviceName,
            files = fileOfferItems,
            totalSizeBytes = totalSizeBytes,
        )

        return httpClient.fileOfferRequest(
           device,
            fileOfferRequest
        )
    }
}