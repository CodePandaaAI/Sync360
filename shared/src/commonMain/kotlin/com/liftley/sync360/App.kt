package com.liftley.sync360

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.liftley.sync360.core.designsystem.AppTheme
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.presentation.DesktopDashboard
import com.liftley.sync360.features.sync.presentation.SendScreen
import com.liftley.sync360.features.sync.presentation.ReceiveScreen
import com.liftley.sync360.features.sync.presentation.SettingsScreen
import com.liftley.sync360.features.sync.presentation.SyncUiState
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
    if (isDesktop) {
        DesktopSyncApp()
    } else {
        MobileSyncApp()
    }
}

@Composable
fun DesktopSyncApp() {
    SyncAppFrame(isDesktop = true) { viewModel, uiState, _ ->
        DesktopDashboard(
            uiState = uiState,
            uiEffects = viewModel.uiEffects,
            onEvent = { viewModel.onEvent(it) }
        )
    }
}

@Composable
fun MobileSyncApp() {
    SyncAppFrame(isDesktop = false) { viewModel, uiState, navigationViewModel ->
        val backStack = navigationViewModel.backStack

        LaunchedEffect(
            uiState.receive.pendingIncomingOffer,
            uiState.receive.fileTransferProgress,
            uiState.receive.receivedFileBatch
        ) {
            val hasIncoming = uiState.receive.pendingIncomingOffer != null ||
                    (uiState.receive.fileTransferProgress != null &&
                            uiState.receive.fileTransferProgress.direction == TransferDirection.RECEIVING) ||
                    uiState.receive.receivedFileBatch != null
            if (hasIncoming && navigationViewModel.currentRoute != SyncRoute.Receive) {
                navigationViewModel.navigate(SyncRoute.Receive)
            }
        }

        NavDisplay(
            backStack = backStack,
            onBack = { navigationViewModel.pop() },
            entryProvider = { key ->
                when (key) {
                    SyncRoute.Send -> NavEntry(key) {
                        SendScreen(
                            uiState = uiState,
                            uiEffects = viewModel.uiEffects,
                            onEvent = { viewModel.onEvent(it) },
                            currentRoute = SyncRoute.Send,
                            showBottomBar = true,
                            showSettingsAction = true,
                            onNavigateRoute = {
                                navigationViewModel.navigateTopLevel(it)
                            },
                            onOpenSettings = {
                                navigationViewModel.navigate(SyncRoute.Settings)
                            }
                        )
                    }

                    SyncRoute.Receive -> NavEntry(key) {
                        ReceiveScreen(
                            uiState = uiState,
                            uiEffects = viewModel.uiEffects,
                            onEvent = { viewModel.onEvent(it) },
                            showBackButton = false,
                            onBack = { navigationViewModel.pop() },
                            currentRoute = SyncRoute.Receive,
                            showBottomBar = true,
                            showSettingsAction = true,
                            onNavigateRoute = {
                                navigationViewModel.navigateTopLevel(it)
                            },
                            onOpenSettings = {
                                navigationViewModel.navigate(SyncRoute.Settings)
                            }
                        )
                    }

                    SyncRoute.Settings -> NavEntry(key) {
                        SettingsScreen(
                            uiState = uiState,
                            uiEffects = viewModel.uiEffects,
                            onEvent = { viewModel.onEvent(it) },
                            onBack = { navigationViewModel.pop() }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun SyncAppFrame(
    isDesktop: Boolean,
    content: @Composable (
        viewModel: SyncViewModel,
        uiState: SyncUiState,
        navigationViewModel: SyncNavigationViewModel
    ) -> Unit
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
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    content(viewModel, uiState, navigationViewModel)
                }
            }
        }
    }
}
