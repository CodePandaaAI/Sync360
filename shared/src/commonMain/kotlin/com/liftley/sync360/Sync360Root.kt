package com.liftley.sync360

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.core.designsystem.icons.Settings
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.navigation.NavScreen
import com.liftley.sync360.presentation.navigation.NavigationViewModel
import com.liftley.sync360.presentation.navigation.TwoPaneScene
import com.liftley.sync360.presentation.navigation.TwoPaneSceneStrategy
import com.liftley.sync360.presentation.receive.ReceiveScreen
import com.liftley.sync360.presentation.receive.ReceiveScreenViewModel
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import com.liftley.sync360.presentation.send.SendScreen
import com.liftley.sync360.presentation.send.SendScreenViewModel
import com.liftley.sync360.presentation.send.model.SendOperationState
import com.liftley.sync360.presentation.settings.SettingsScreen
import org.koin.compose.koinInject

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Sync360Root() {
    val navigationViewModel = koinInject<NavigationViewModel>()
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val sendScreenViewModel = koinInject<SendScreenViewModel>()

    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    val sendScreenState by sendScreenViewModel.screenState.collectAsStateWithLifecycle()
    val currentScreen = navigationViewModel.checkCurrentTop()

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val useTwoPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val twoPaneStrategy = remember(windowSizeClass) {
        TwoPaneSceneStrategy<NavScreen>(windowSizeClass)
    }

    val receiveTitle = when (receiveScreenState) {
        ReceiveScreenState.Idle -> "Receive"
        is ReceiveScreenState.IncomingTextOffer -> "Incoming text"
        is ReceiveScreenState.IncomingFileOffer -> "Incoming files"
        is ReceiveScreenState.ReceivingFiles -> "Receiving files"
        is ReceiveScreenState.ReceivedText -> "Received text"
        is ReceiveScreenState.ReceivedFiles -> "Files received"
    }
    val sendTitle = when (sendScreenState.sendOperationState) {
        SendOperationState.Idle -> "Sync360"
        SendOperationState.Cancelled -> "Sending Cancelled"
        is SendOperationState.SendingTextOffer -> "Sending Text Offer"
        is SendOperationState.SendingFileOffer -> "Sending File Offer"
        is SendOperationState.SendingFile -> "Sending Files"
        is SendOperationState.TextSent -> "Text Sent"
        is SendOperationState.FilesSent -> "Files Sent"
        is SendOperationState.OperationFailed -> "Could Not Send"
    }

    Scaffold(
        bottomBar = {
            if (!useTwoPane && currentScreen != NavScreen.SettingsScreen) {
                NavigationBar(
                    modifier = Modifier
                        // 1. Fetch system bar insets dynamically to protect the Android gesture area
                        .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                        // 2. Add outer floating padding around the bar (converted from dp)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        // 3. Clip the corners after padding to create the floating card shape
                        .clip(MaterialTheme.shapes.extraLarge),
                    containerColor = MaterialTheme.colorScheme.surface,
                    // 4. Disable internal inset consumption so our custom modifiers control the shape
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationBarItem(
                        onClick = {
                            navigationViewModel.addScreen(NavScreen.ReceiveScreen)
                        },
                        selected = navigationViewModel.checkCurrentTop() ==
                                NavScreen.ReceiveScreen,
                        label = { Text("Receive") },
                        icon = {
                            Icon(
                                imageVector = Download,
                                contentDescription = null
                            )
                        }
                    )
                    NavigationBarItem(
                        onClick = navigationViewModel::removeAllExceptAddScreen,
                        selected = navigationViewModel.checkCurrentTop() ==
                                NavScreen.SendScreen,
                        label = { Text("Send") },
                        icon = {
                            Icon(
                                imageVector = Send,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (currentScreen == NavScreen.SettingsScreen) {
                        IconButton(
                            modifier = Modifier.height(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            onClick = navigationViewModel::removeLast
                        ) {
                            Icon(
                                imageVector = Close,
                                contentDescription = "Close settings"
                            )
                        }
                    }
                },
                title = {
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.extraLarge)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (currentScreen) {
                                NavScreen.SendScreen -> sendTitle
                                NavScreen.ReceiveScreen -> receiveTitle
                                NavScreen.SettingsScreen -> "Settings"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    if (currentScreen != NavScreen.SettingsScreen) {
                        IconButton(
                            modifier = Modifier.height(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            onClick = {
                                navigationViewModel.addScreen(NavScreen.SettingsScreen)
                            }
                        ) {
                            Icon(
                                imageVector = Settings,
                                contentDescription = "Open settings"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        NavDisplay(
            backStack = navigationViewModel.backstack,
            modifier = Modifier.padding(innerPadding),
            onBack = navigationViewModel::removeLast,
            sceneStrategies = listOf(twoPaneStrategy)
        ) { screen ->
            when (screen) {
                is NavScreen.SendScreen -> {
                    NavEntry(
                        key = screen,
                        metadata = TwoPaneScene.firstPane()
                    ) {
                        SendScreen(
                            onTroubleshootClick = {
                                navigationViewModel.addScreen(NavScreen.SettingsScreen)
                            }
                        )
                    }
                }

                is NavScreen.ReceiveScreen -> {
                    NavEntry(
                        key = screen,
                        metadata = TwoPaneScene.secondPane()
                    ) {
                        ReceiveScreen(
                            onTroubleshootClick = {
                                navigationViewModel.addScreen(NavScreen.SettingsScreen)
                            }
                        )
                    }
                }

                is NavScreen.SettingsScreen -> {
                    NavEntry(key = screen) {
                        SettingsScreen(
                            repairEnabled = sendScreenState.sendOperationState == SendOperationState.Idle &&
                                    receiveScreenState == ReceiveScreenState.Idle &&
                                    (sendScreenState.discoveryStatus == DiscoveryStatus.Idle ||
                                            sendScreenState.discoveryStatus == DiscoveryStatus.Running),
                            onRepairClick = sendScreenViewModel::repairNetworkServices
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(receiveScreenState) {
        if (receiveScreenState is ReceiveScreenState.IncomingTextOffer ||
            receiveScreenState is ReceiveScreenState.IncomingFileOffer
        ) {
            navigationViewModel.addScreen(NavScreen.ReceiveScreen)
        }
    }
}
