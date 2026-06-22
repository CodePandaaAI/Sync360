package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState

data class RuntimeUiState(
    val localDeviceName: String = "This device",
    val serverIp: String = "127.0.0.1",
    val runtimeState: SyncRuntimeState = SyncRuntimeState.Stopped,
    val localNetworkHealthy: Boolean = true
)
