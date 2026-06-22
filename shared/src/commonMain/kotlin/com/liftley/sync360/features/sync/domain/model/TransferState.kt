package com.liftley.sync360.features.sync.domain.model

sealed interface TransferState {
    data object Idle : TransferState
    data class Preparing(val progress: FileTransferProgress) : TransferState
    data class Transferring(val progress: FileTransferProgress) : TransferState
    data class Verifying(val progress: FileTransferProgress) : TransferState
    data class Succeeded(
        val direction: TransferDirection,
        val receivedBatch: ReceivedFileBatch? = null
    ) : TransferState
    data class Failed(val failure: FileTransferFailure) : TransferState
}

data class TransferSnapshot(
    val state: TransferState = TransferState.Idle,
    val failure: FileTransferFailure? = null,
    val receivedBatch: ReceivedFileBatch? = null
) {
    val progress: FileTransferProgress?
        get() = when (state) {
            is TransferState.Preparing -> state.progress
            is TransferState.Transferring -> state.progress
            is TransferState.Verifying -> state.progress
            else -> null
        }

    val isActive: Boolean
        get() = progress != null

    val blocksIncomingOffers: Boolean
        get() = when (val current = state) {
            TransferState.Idle,
            is TransferState.Failed -> false
            is TransferState.Succeeded -> current.direction == TransferDirection.RECEIVING &&
                current.receivedBatch != null
            is TransferState.Preparing,
            is TransferState.Transferring,
            is TransferState.Verifying -> true
        }
}
