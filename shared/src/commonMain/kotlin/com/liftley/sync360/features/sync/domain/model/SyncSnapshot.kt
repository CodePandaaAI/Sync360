package com.liftley.sync360.features.sync.domain.model

data class SyncSnapshot(
    val runtime: SyncRuntimeState = SyncRuntimeState.Stopped,
    val transfer: TransferSnapshot = TransferSnapshot(),
    val quickSaveEnabled: Boolean = false,
    val pendingIncomingOffer: PendingIncomingOffer? = null
)
