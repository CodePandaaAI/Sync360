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
import com.liftley.sync360.core.designsystem.AppTheme
import com.liftley.sync360.features.sync.presentation.DesktopDashboard
import com.liftley.sync360.features.sync.presentation.SyncScreen
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import com.liftley.sync360.features.sync.presentation.navigation.SyncNavigationViewModel
import com.liftley.sync360.features.sync.presentation.navigation.SyncRoute
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun App(
    isDesktop: Boolean
) {
    KoinContext {
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
                    val viewModel: SyncViewModel = koinInject { parametersOf(isDesktop) }
                    val navigationViewModel: SyncNavigationViewModel = koinInject()
                    val uiState by viewModel.uiState.collectAsState()
                    val backStack by navigationViewModel.backStack.collectAsState()

                    when (backStack.last()) {
                        SyncRoute.Home -> {
                            if (isDesktop) {
                                DesktopDashboard(
                                    uiState = uiState,
                                    uiEffects = viewModel.uiEffects,
                                    onEvent = { viewModel.onEvent(it) }
                                )
                            } else {
                                SyncScreen(
                                    uiState = uiState,
                                    uiEffects = viewModel.uiEffects,
                                    onEvent = { viewModel.onEvent(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
