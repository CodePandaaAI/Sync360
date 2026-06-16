package com.liftley.sync360.features.sync.data.repository

import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import com.liftley.sync360.features.sync.domain.model.TransferStage
import com.liftley.sync360.features.sync.domain.model.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class TransferStore {
    private val _snapshot = MutableStateFlow(TransferSnapshot())
    val snapshot: StateFlow<TransferSnapshot> = _snapshot.asStateFlow()

    val value: TransferSnapshot
        get() = snapshot.value

    fun start(progress: FileTransferProgress) {
        _snapshot.value = TransferSnapshot(
            state = progress.toState(),
            failure = null,
            receivedBatch = null
        )
    }

    fun updateProgress(bytesTransferred: Long, speedBytesPerSecond: Long?, estimatedTimeRemainingSeconds: Long?) {
        val progress = value.progress ?: return
        transition(progress.copy(
            bytesTransferred = bytesTransferred.coerceAtMost(progress.totalBytes),
            speedBytesPerSecond = speedBytesPerSecond,
            estimatedTimeRemainingSeconds = estimatedTimeRemainingSeconds
        ))
    }

    fun updateStage(stage: TransferStage) {
        val progress = value.progress ?: return
        transition(progress.copy(stage = stage))
    }

    fun succeed(direction: TransferDirection, batch: ReceivedFileBatch? = null) {
        val completedBatch = batch ?: value.receivedBatch
        _snapshot.value = TransferSnapshot(
            state = TransferState.Succeeded(direction, completedBatch),
            receivedBatch = completedBatch
        )
    }

    fun fail(failure: FileTransferFailure) {
        _snapshot.value = TransferSnapshot(
            state = TransferState.Failed(failure),
            failure = failure
        )
    }

    fun dismissFailure() {
        _snapshot.value = value.copy(
            state = if (value.state is TransferState.Failed) TransferState.Idle else value.state,
            failure = null
        )
    }

    fun dismissReceivedBatch() {
        _snapshot.value = value.copy(
            state = if (value.state is TransferState.Succeeded) TransferState.Idle else value.state,
            receivedBatch = null
        )
    }

    fun clear() {
        _snapshot.value = TransferSnapshot()
    }

    private fun transition(progress: FileTransferProgress) {
        _snapshot.value = value.copy(state = progress.toState())
    }
}

private fun FileTransferProgress.toState(): TransferState = when (stage) {
    TransferStage.PREPARING -> TransferState.Preparing(this)
    TransferStage.TRANSFERRING -> TransferState.Transferring(this)
    TransferStage.VERIFYING -> TransferState.Verifying(this)
}
