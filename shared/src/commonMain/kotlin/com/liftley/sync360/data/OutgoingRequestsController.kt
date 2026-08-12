package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.client.Sync360HttpClient
import com.liftley.sync360.data.network.http.dto.CancelRequest
import com.liftley.sync360.data.network.http.dto.CancelResponse
import com.liftley.sync360.data.network.http.dto.file.FileOfferItem
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferRequest
import com.liftley.sync360.data.network.http.dto.text.TextTransferResponse
import com.liftley.sync360.data.network.tcp.FileTransferSender
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import kotlin.uuid.Uuid

class OutgoingRequestsController(
    private val httpClient: Sync360HttpClient,
    private val localDeviceInfoProvider: LocalDeviceInfoProvider,
    private val fileTransferSender: FileTransferSender
) {
    fun cancelCurrentFileTransfer() {
        fileTransferSender.cancelCurrentFileTransfer()
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
        text: String,
        operationId: Uuid
    ): Result<TextTransferResponse> {
        val myDeviceInfo = localDeviceInfoProvider.getLocalDeviceInfo()

        val textOfferRequest = TextOfferRequest(
            operationId = operationId,
            senderDeviceId = myDeviceInfo.deviceId,
            senderDeviceName = myDeviceInfo.deviceName,
            preview = text.take(200),
            characterCount = text.count()
        )

        val textTransferRequest = TextTransferRequest(
            operationId = operationId,
            senderDeviceId = myDeviceInfo.deviceId,
            text = text
        )

        return httpClient.textTransferRequest(
            deviceToSendText,
            textOfferRequest,
            textTransferRequest
        )
    }

    suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        selectedFiles: List<SelectedFile>,
        operationId: Uuid,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit,
        onProgress: (FileTransferProgress) -> Unit
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
            offeredFiles = offeredFiles,
            totalSizeBytes = totalSizeBytes
        )

        httpClient.sendFilesToDevice(deviceToSendFiles, fileOfferRequest).fold(
            onSuccess = {
                return fileTransferSender.sendFiles(
                    deviceToSendFiles = deviceToSendFiles,
                    files = selectedFiles,
                    operationId = operationId,
                    onFileStarted = onFileStarted,
                    onProgress = onProgress
                )
            },
            onFailure = { error ->
                return Result.failure(error)
            }
        )
    }
}
