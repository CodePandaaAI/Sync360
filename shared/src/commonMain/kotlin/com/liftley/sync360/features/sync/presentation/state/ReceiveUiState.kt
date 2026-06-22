package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch

data class ReceiveUiState(
    val pendingIncomingOffer: PendingIncomingOffer? = null,
    val quickSaveEnabled: Boolean = false,
    val fileTransferProgress: FileTransferProgress? = null,
    val fileTransferFailure: FileTransferFailure? = null,
    val receivedFileBatch: ReceivedFileBatch? = null
)
