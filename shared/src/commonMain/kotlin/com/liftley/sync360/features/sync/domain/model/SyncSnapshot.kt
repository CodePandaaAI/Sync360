package com.liftley.sync360.features.sync.domain.model

data class SyncSnapshot(
    val runtime: SyncRuntimeState = SyncRuntimeState.Stopped,
    val connection: ConnectionSnapshot = ConnectionSnapshot(),
    val session: SessionSnapshot = SessionSnapshot.NoSession,
    val transfer: TransferSnapshot = TransferSnapshot()
)
