package com.liftley.sync360.features.sync.presentation

sealed interface SyncUiEffect {
    data class ShowMessage(val message: String) : SyncUiEffect
}
