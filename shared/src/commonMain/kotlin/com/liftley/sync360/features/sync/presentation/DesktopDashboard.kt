package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

@Composable
fun DesktopDashboard(
    uiState: SyncUiState,
    uiEffects: Flow<SyncUiEffect>,
    onEvent: (SyncEvent) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            SendScreen(
                uiState = uiState,
                uiEffects = uiEffects,
                onEvent = onEvent
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            ReceiveScreen(
                uiState = uiState,
                uiEffects = uiEffects,
                onEvent = onEvent,
                showBackButton = false
            )
        }
    }
}
