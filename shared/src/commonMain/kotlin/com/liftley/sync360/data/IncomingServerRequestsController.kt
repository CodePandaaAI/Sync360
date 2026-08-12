package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.data.network.http.dto.text.TextOfferRequest
import com.liftley.sync360.domain.model.ClientServerState
import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.UserDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class IncomingServerRequestsController {
    private val _clientServerState = MutableStateFlow<ClientServerState>(ClientServerState.Idle)
    val clientServerState: StateFlow<ClientServerState> = _clientServerState.asStateFlow()

    private val operationMutex = Mutex()
    private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingUserDecision: CompletableDeferred<UserDecision>? = null

    init {
        operationScope.launch {
            clientServerState.collectLatest { state ->
                val operationId = when (state) {
                    is ClientServerState.WaitingForText -> state.textOffer.operationId
                    is ClientServerState.WaitingForFiles -> state.fileOffer.operationId
                    else -> return@collectLatest
                }

                delay(ACCEPTED_OPERATION_TIMEOUT_MILLIS.milliseconds)
                expireOperation(operationId)
            }
        }
    }

    internal suspend fun awaitTextOfferDecision(
        textOffer: TextOfferRequest
    ): UserDecision? = awaitUserDecision(
        operationId = textOffer.operationId,
        offerState = ClientServerState.TextOffer(textOffer)
    )

    internal suspend fun awaitFileOfferDecision(
        fileOffer: FileOfferRequest
    ): UserDecision? = awaitUserDecision(
        operationId = fileOffer.operationId,
        offerState = ClientServerState.FileOffer(fileOffer)
    )

    private suspend fun awaitUserDecision(
        operationId: Uuid,
        offerState: ClientServerState
    ): UserDecision? {
        val decision = operationMutex.withLock {
            if (_clientServerState.value != ClientServerState.Idle) {
                return@withLock null
            }

            CompletableDeferred<UserDecision>().also { decision ->
                pendingUserDecision = decision
                _clientServerState.value = offerState
            }
        } ?: return null

        val result = try {
            withTimeoutOrNull(OFFER_DECISION_TIMEOUT_MILLIS.milliseconds) {
                decision.await()
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                expireOperation(operationId)
            }
            throw exception
        }

        if (result == null) {
            expireOperation(operationId)
        }

        return result ?: UserDecision.DECLINED
    }

    suspend fun makeDecision(decision: UserDecision) {
        val waitingDecision = operationMutex.withLock {
            val currentDecision = pendingUserDecision ?: return@withLock null

            _clientServerState.value = when (val state = _clientServerState.value) {
                is ClientServerState.TextOffer -> {
                    if (decision == UserDecision.ACCEPTED) {
                        ClientServerState.WaitingForText(state.textOffer)
                    } else {
                        ClientServerState.Idle
                    }
                }

                is ClientServerState.FileOffer -> {
                    if (decision == UserDecision.ACCEPTED) {
                        ClientServerState.WaitingForFiles(state.fileOffer)
                    } else {
                        ClientServerState.Idle
                    }
                }

                else -> return@withLock null
            }

            pendingUserDecision = null
            currentDecision
        } ?: return

        waitingDecision.complete(decision)
    }

    private suspend fun expireOperation(operationId: Uuid) {
        val waitingDecision = operationMutex.withLock {
            if (!_clientServerState.value.matchesExpirableOperation(operationId)) {
                return@withLock null
            }

            _clientServerState.value = ClientServerState.Idle
            pendingUserDecision.also { pendingUserDecision = null }
        }

        waitingDecision?.complete(UserDecision.DECLINED)
    }

    internal suspend fun cancelOperation(
        operationId: Uuid,
        senderDeviceId: String
    ): Boolean {
        val cancellation = operationMutex.withLock {
            if (!_clientServerState.value.matchesOperation(operationId, senderDeviceId)) {
                return@withLock null
            }

            _clientServerState.value = ClientServerState.Idle
            Cancellation(
                waitingDecision = pendingUserDecision.also {
                    pendingUserDecision = null
                }
            )
        } ?: return false

        cancellation.waitingDecision?.complete(UserDecision.CANCELLED)
        return true
    }

    suspend fun prepareAcceptedFileTransfer(
        operationId: Uuid,
        prepare: () -> Unit
    ): Boolean = operationMutex.withLock {
        val state = _clientServerState.value as? ClientServerState.WaitingForFiles
            ?: return@withLock false
        if (state.fileOffer.operationId != operationId) return@withLock false

        prepare()
        _clientServerState.value = ClientServerState.ReceivingFiles(
            fileOffer = state.fileOffer,
            completedFileCount = 0,
            progress = FileTransferProgress.waiting(state.fileOffer.totalSizeBytes)
        )
        true
    }

    suspend fun receiveAcceptedText(
        operationId: Uuid,
        senderDeviceId: String,
        text: String
    ): Boolean = operationMutex.withLock {
        val state = _clientServerState.value as? ClientServerState.WaitingForText
            ?: return@withLock false
        if (
            state.textOffer.operationId != operationId ||
            state.textOffer.senderDeviceId != senderDeviceId
        ) return@withLock false

        _clientServerState.value = ClientServerState.TextReceived(text)
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
            is ClientServerState.TextOffer -> textOffer.operationId to textOffer.senderDeviceId
            is ClientServerState.WaitingForText -> textOffer.operationId to textOffer.senderDeviceId
            is ClientServerState.FileOffer -> fileOffer.operationId to fileOffer.senderDeviceId
            is ClientServerState.WaitingForFiles -> fileOffer.operationId to fileOffer.senderDeviceId
            is ClientServerState.ReceivingFiles -> fileOffer.operationId to fileOffer.senderDeviceId
            else -> return false
        }

        return operation.first == operationId &&
            operation.second == senderDeviceId
    }

    private fun ClientServerState.matchesExpirableOperation(operationId: Uuid): Boolean {
        val currentOperationId = when (this) {
            is ClientServerState.TextOffer -> textOffer.operationId
            is ClientServerState.WaitingForText -> textOffer.operationId
            is ClientServerState.FileOffer -> fileOffer.operationId
            is ClientServerState.WaitingForFiles -> fileOffer.operationId
            else -> return false
        }

        return currentOperationId == operationId
    }

    private data class Cancellation(
        val waitingDecision: CompletableDeferred<UserDecision>?
    )

    private companion object {
        const val OFFER_DECISION_TIMEOUT_MILLIS = 50_000L
        const val ACCEPTED_OPERATION_TIMEOUT_MILLIS = 30_000L
    }
}
