package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.file.FileOfferResponse
import com.liftley.sync360.data.network.http.dto.file.FileOfferStatus
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryRequest
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryResponse
import com.liftley.sync360.data.network.http.dto.text.TextDeliveryStatus
import com.liftley.sync360.domain.model.ClientServerState
import com.liftley.sync360.domain.model.FileReceiveCode
import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.TextDeliveryLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

class IncomingServerRequestsController {
    private val _clientServerState = MutableStateFlow<ClientServerState>(ClientServerState.Idle)
    val clientServerState: StateFlow<ClientServerState> = _clientServerState.asStateFlow()
    val fileReceiveCode: String = FileReceiveCode.generate()

    private val operationMutex = Mutex()

    internal suspend fun deliverIncomingText(
        request: TextDeliveryRequest
    ): TextDeliveryResponse {
        if (request.text.length > TextDeliveryLimits.MAX_CHARACTER_COUNT) {
            return TextDeliveryResponse(
                status = TextDeliveryStatus.TEXT_TOO_LARGE
            )
        }

        return operationMutex.withLock {
            if (_clientServerState.value != ClientServerState.Idle) {
                return@withLock TextDeliveryResponse(
                    status = TextDeliveryStatus.RECEIVER_BUSY
                )
            }

            _clientServerState.value = ClientServerState.TextReceived(
                senderDeviceName = request.senderDeviceName,
                text = request.text
            )

            TextDeliveryResponse(
                status = TextDeliveryStatus.DELIVERED
            )
        }
    }

    internal suspend fun prepareIncomingFileTransfer(
        fileOffer: FileOfferRequest,
        prepare: (acceptedFileOffer: FileOfferRequest) -> Unit
    ): FileOfferResponse = operationMutex.withLock {
        if (_clientServerState.value != ClientServerState.Idle) {
            return@withLock FileOfferResponse(
                status = FileOfferStatus.RECEIVER_BUSY
            )
        }

        if (fileOffer.receiveCode != fileReceiveCode) {
            return@withLock FileOfferResponse(
                status = FileOfferStatus.INVALID_CODE
            )
        }

        val acceptedFileOffer = fileOffer.copy(receiveCode = "")

        try {
            prepare(acceptedFileOffer)
        } catch (exception: Exception) {
            exception.printStackTrace()
            return@withLock FileOfferResponse(
                status = FileOfferStatus.PREPARATION_FAILED
            )
        }

        _clientServerState.value = ClientServerState.ReceivingFiles(
            fileOffer = acceptedFileOffer,
            completedFileCount = 0,
            progress = FileTransferProgress.waiting(acceptedFileOffer.totalSizeBytes)
        )

        FileOfferResponse(
            status = FileOfferStatus.ACCEPTED
        )
    }

    internal suspend fun cancelOperation(
        operationId: Uuid,
        senderDeviceId: String
    ): Boolean = operationMutex.withLock {
        if (!_clientServerState.value.matchesOperation(operationId, senderDeviceId)) {
            return@withLock false
        }

        _clientServerState.value = ClientServerState.Idle
        true
    }

    fun updateFileProgress(
        operationId: Uuid,
        progress: FileTransferProgress
    ) {
        _clientServerState.update { state ->
            if (
                state is ClientServerState.ReceivingFiles &&
                state.fileOffer.operationId == operationId
            ) {
                state.copy(progress = progress)
            } else {
                state
            }
        }
    }

    fun updateCompletedFileCount(
        operationId: Uuid,
        completedFileCount: Int
    ) {
        _clientServerState.update { state ->
            if (
                state is ClientServerState.ReceivingFiles &&
                state.fileOffer.operationId == operationId
            ) {
                state.copy(completedFileCount = completedFileCount)
            } else {
                state
            }
        }
    }

    suspend fun finishFileTransfer(
        operationId: Uuid,
        wasSuccessful: Boolean
    ) {
        operationMutex.withLock {
            val state = _clientServerState.value as? ClientServerState.ReceivingFiles
                ?: return@withLock
            if (state.fileOffer.operationId != operationId) return@withLock

            _clientServerState.value = if (wasSuccessful) {
                ClientServerState.FilesReceived(
                    senderDeviceName = state.fileOffer.senderDeviceName,
                    fileCount = state.fileOffer.offeredFiles.size
                )
            } else {
                ClientServerState.Idle
            }
        }
    }

    suspend fun clearState() {
        operationMutex.withLock {
            when (_clientServerState.value) {
                ClientServerState.Idle,
                is ClientServerState.TextReceived,
                is ClientServerState.FilesReceived -> {
                    _clientServerState.value = ClientServerState.Idle
                }

                else -> Unit
            }
        }
    }

    private fun ClientServerState.matchesOperation(
        operationId: Uuid,
        senderDeviceId: String
    ): Boolean {
        val operation = when (this) {
            is ClientServerState.ReceivingFiles -> fileOffer.operationId to fileOffer.senderDeviceId
            else -> return false
        }

        return operation.first == operationId &&
            operation.second == senderDeviceId
    }
}
