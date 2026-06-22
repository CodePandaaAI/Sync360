package com.liftley.sync360.features.sync.presentation

data class SyncUiState(
    val runtime: RuntimeUiState = RuntimeUiState(),
    val discovery: DiscoveryUiState = DiscoveryUiState(),
    val send: SendUiState = SendUiState(),
    val receive: ReceiveUiState = ReceiveUiState()
)
