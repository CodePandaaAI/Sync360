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
        deviceToSendOfferInfo: NearbyDevice,
        text: String
    ): Result<TextTransferResponse> {
        val myDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val textOfferRequest = TextOfferRequest(
            senderDeviceId = myDeviceInfo.deviceId,
            senderDeviceName = myDeviceInfo.deviceName,
            preview = text.take(180),
            characterCount = text.count()
        )

        val textTransferRequest = TextTransferRequest(
            text = text
        )

        return httpClient.textTransferRequest(
            deviceToSendOfferInfo,
            textOfferRequest = textOfferRequest,
            textTransferRequest = textTransferRequest
        )
    }

    suspend fun sendFileOffer(
        deviceToSendOfferInfo: NearbyDevice,
        selectedFilesToSend: List<PickedFile>
    ): Result<FileOfferResponse> {
        val myDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val totalSizeBytes = if (selectedFilesToSend.all { it.sizeBytes != null }) {
            selectedFilesToSend.sumOf { it.sizeBytes ?: 0L }
        } else {
            null
        }

        val selectedFilesAsFileOfferItem = selectedFilesToSend.map {
            FileOfferItem(
                fileName = it.displayName,
                fileSizeBytes = it.sizeBytes,
                mimeType = it.mimeType
            )
        }

        val fileOfferRequest = FileOfferRequest(
            senderDeviceId = myDeviceInfo.deviceId,
            senderDeviceName = myDeviceInfo.deviceName,
            files = selectedFilesAsFileOfferItem,
            totalSizeBytes = totalSizeBytes,
        )

        return httpClient.fileOfferRequest(
           deviceToSendOfferInfo,
            fileOfferRequest
        )
    }
}