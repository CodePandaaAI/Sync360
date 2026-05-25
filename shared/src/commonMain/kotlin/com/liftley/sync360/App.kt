package com.liftley.sync360

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liftley.sync360.core.designsystem.AppTheme
import com.liftley.sync360.core.di.AppContainer
import com.liftley.sync360.features.sync.presentation.DesktopDashboard
import com.liftley.sync360.features.sync.presentation.SyncScreen
import com.liftley.sync360.features.sync.presentation.SyncViewModel

@Composable
fun App(
    isDesktop: Boolean,
    container: AppContainer
) {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isDesktop) Modifier.safeContentPadding()
                        else Modifier
                    )
            ) {
                val viewModel: SyncViewModel = viewModel {
                    container.syncViewModel(isDesktop)
                }
                val uiState by viewModel.uiState.collectAsState()

                if (isDesktop) {
                    DesktopDashboard(
                        uiState = uiState,
                        onEvent = { viewModel.onEvent(it) }
                    )
                } else {
                    SyncScreen(
                        uiState = uiState,
                        onEvent = { viewModel.onEvent(it) }
                    )
                }
            }
        }
    }
}
