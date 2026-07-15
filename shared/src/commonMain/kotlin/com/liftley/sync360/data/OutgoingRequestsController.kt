package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.client.Sync360HttpClient
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.data.network.tcp.FileTransferSender
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.FileTransferOffer
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.domain.model.OfferedFile

class OutgoingRequestsController(
    private val httpClient: Sync360HttpClient,
    private val localDeviceInfoProvider: LocalDeviceInfoProvider,
    private val fileTransferSender: FileTransferSender
) {
    fun cancelCurrentFileTransfer() {
        fileTransferSender.cancelCurrentTransfer()
    }

    suspend fun sendText(
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
            textOfferRequest,
            textTransferRequest
        )
    }

    suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        selectedFiles: List<SelectedFile>,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit
    ): Result<Unit> {
        if (selectedFiles.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("No files were selected")
            )
        }

        val fileWithUnknownSize = selectedFiles.firstOrNull {
            it.sizeBytes == null
        }

        if (fileWithUnknownSize != null) {
            return Result.failure(
                IllegalArgumentException(
                    "File size is unknown: ${fileWithUnknownSize.displayName}"
                )
            )
        }

        val myDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val offeredFiles = selectedFiles.mapIndexed { index, file ->
            OfferedFile(
                index = index,
                fileName = file.displayName,
                fileSizeBytes = requireNotNull(file.sizeBytes),
                mimeType = file.mimeType
            )
        }

        val totalSizeBytes = selectedFiles.sumOf {
            requireNotNull(it.sizeBytes)
        }

        val fileOffer = FileTransferOffer(
            senderDeviceId = myDeviceInfo.deviceId,
            senderDeviceName = myDeviceInfo.deviceName,
            files = offeredFiles,
            totalSizeBytes = totalSizeBytes
        )

        val offerResult = httpClient.sendFileOffer(
            device = deviceToSendFiles,
            fileOffer = fileOffer
        )

        if (offerResult.isFailure) {
            return Result.failure(
                offerResult.exceptionOrNull()
                    ?: Exception("File offer failed")
            )
        }

        return fileTransferSender.sendFiles(
            device = deviceToSendFiles,
            files = selectedFiles,
            onFileStarted = onFileStarted
        )
    }
}
