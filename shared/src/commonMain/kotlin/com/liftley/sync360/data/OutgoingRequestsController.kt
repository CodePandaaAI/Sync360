package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.client.FileOfferException
import com.liftley.sync360.data.network.http.client.Sync360HttpClient
import com.liftley.sync360.data.network.http.client.TextDeliveryException
import com.liftley.sync360.data.network.http.dto.CancelRequest
import com.liftley.sync360.data.network.http.dto.CancelResponse
import com.liftley.sync360.data.network.http.dto.file.FileOfferItem
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferStatus
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryRequest
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryStatus
import com.liftley.sync360.data.network.tcp.FileTransmitter
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.FileReceiveCode
import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.domain.model.TextDeliveryLimits
import kotlin.uuid.Uuid

class OutgoingRequestsController(
    private val httpClient: Sync360HttpClient,
    private val localDeviceInfoProvider: LocalDeviceInfoProvider,
    private val fileTransmitter: FileTransmitter
) {
    fun cancelCurrentFileTransfer() {
        fileTransmitter.cancelCurrentFileTransfer()
    }

    suspend fun sendCancellationRequestToTargetDevice(
        targetDevice: NearbyDevice,
        operationId: Uuid
    ): Result<CancelResponse> {
        val myDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        return httpClient.cancelOperation(
            targetDevice = targetDevice,
            cancelRequest = CancelRequest(
                operationId = operationId,
                senderDeviceId = myDeviceInfo.deviceId
            )
        )
    }

    suspend fun sendText(
        deviceToSendText: NearbyDevice,
        text: String
    ): Result<Unit> {
        if (text.isBlank()) {
            return Result.failure(
                TextDeliveryException("Text cannot be empty")
            )
        }

        if (text.length > TextDeliveryLimits.MAX_CHARACTER_COUNT) {
            return Result.failure(
                TextDeliveryException(
                    "Text cannot exceed " +
                            "${TextDeliveryLimits.MAX_CHARACTER_COUNT} characters"
                )
            )
        }

        val myDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val request = TextDeliveryRequest(
            senderDeviceName = myDeviceInfo.deviceName,
            text = text
        )

        val response = httpClient.deliverText(
            targetDevice = deviceToSendText,
            request = request
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        return when (response.status) {
            TextDeliveryStatus.DELIVERED -> {
                Result.success(Unit)
            }

            TextDeliveryStatus.RECEIVER_BUSY -> {
                Result.failure(
                    TextDeliveryException(
                        "${deviceToSendText.deviceName} is currently busy"
                    )
                )
            }

            TextDeliveryStatus.TEXT_TOO_LARGE -> {
                Result.failure(
                    TextDeliveryException(
                        "The text exceeds the receiver's character limit"
                    )
                )
            }
        }
    }

    suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        selectedFiles: List<SelectedFile>,
        receiveCode: String,
        operationId: Uuid,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit,
        onProgress: (FileTransferProgress) -> Unit
    ): Result<Unit> {
        if (selectedFiles.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("No files were selected")
            )
        }

        if (!FileReceiveCode.isValid(receiveCode)) {
            return Result.failure(
                FileOfferException("Enter the receiver's four-digit code")
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
            FileOfferItem(
                index = index,
                fileName = file.displayName,
                fileSizeBytes = requireNotNull(file.sizeBytes),
                mimeType = file.mimeType
            )
        }

        val totalSizeBytes = selectedFiles.sumOf {
            requireNotNull(it.sizeBytes)
        }

        val fileOfferRequest = FileOfferRequest(
            operationId = operationId,
            senderDeviceId = myDeviceInfo.deviceId,
            senderDeviceName = myDeviceInfo.deviceName,
            receiveCode = receiveCode,
            offeredFiles = offeredFiles,
            totalSizeBytes = totalSizeBytes
        )

        val response = httpClient.sendFileOfferRequestToDevice(
            deviceToSendFiles = deviceToSendFiles,
            fileOfferRequest = fileOfferRequest
        ).getOrElse { exception ->
            return Result.failure(exception)
        }

        when (response.status) {
            FileOfferStatus.ACCEPTED -> Unit

            FileOfferStatus.INVALID_CODE -> {
                return Result.failure(
                    FileOfferException("The receive code is incorrect")
                )
            }

            FileOfferStatus.RECEIVER_BUSY -> {
                return Result.failure(
                    FileOfferException(
                        "${deviceToSendFiles.deviceName} is currently busy"
                    )
                )
            }

            FileOfferStatus.PREPARATION_FAILED -> {
                return Result.failure(
                    FileOfferException(
                        "${deviceToSendFiles.deviceName} could not prepare for the file transfer"
                    )
                )
            }
        }

        return fileTransmitter.sendFiles(
            deviceToSendFiles = deviceToSendFiles,
            files = selectedFiles,
            operationId = operationId,
            onFileStarted = onFileStarted,
            onProgress = onProgress
        )
    }
}
