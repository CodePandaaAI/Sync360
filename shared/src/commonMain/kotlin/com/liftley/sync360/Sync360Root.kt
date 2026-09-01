package com.liftley.sync360

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.liftley.sync360.core.designsystem.icons.Back
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.core.designsystem.icons.Settings
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.presentation.navigation.NavScreen
import com.liftley.sync360.presentation.navigation.NavigationViewModel
import com.liftley.sync360.presentation.navigation.TwoPaneScene
import com.liftley.sync360.presentation.navigation.TwoPaneSceneStrategy
import com.liftley.sync360.presentation.receive.ReceiveScreen
import com.liftley.sync360.presentation.receive.ReceiveScreenViewModel
import com.liftley.sync360.presentation.receive.model.ReceiveState
import com.liftley.sync360.presentation.send.SendScreen
import com.liftley.sync360.presentation.send.SendScreenViewModel
import com.liftley.sync360.presentation.send.model.SendState
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
    val sendScreenState by sendScreenViewModel.sendScreenState.collectAsStateWithLifecycle()
    val currentScreen = navigationViewModel.currentScreen()

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val useTwoPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val twoPaneStrategy = remember(windowSizeClass) {
        TwoPaneSceneStrategy<NavScreen>(windowSizeClass)
    }

    val discoveryIsStable =
        sendScreenState.discoveryStatus == DiscoveryStatus.Idle ||
                sendScreenState.discoveryStatus == DiscoveryStatus.Running
    val registrationIsStable =
        sendScreenState.registrationStatus == RegistrationStatus.Idle ||
                sendScreenState.registrationStatus == RegistrationStatus.Running
    val repairEnabled =
        sendScreenState.sendState == SendState.Idle &&
                receiveScreenState is ReceiveState.Idle &&
                discoveryIsStable &&
                registrationIsStable

    val shouldKeepScreenOn =
        sendScreenState.sendState != SendState.Idle ||
                receiveScreenState !is ReceiveState.Idle

    val receiveTitle = when (receiveScreenState) {
        is ReceiveState.Idle -> "Sync360"
        is ReceiveState.ReceivingFiles -> "Receiving files"
        is ReceiveState.ReceivedText -> "Received text"
        is ReceiveState.ReceivedFiles -> "Files received"
    }

    val sendTitle = when (sendScreenState.sendState) {
        SendState.Idle -> "Sync360"
        SendState.Cancelled -> "Sending Cancelled"
        is SendState.SendingText -> "Sending Text"
        is SendState.PreparingFiles -> "Preparing Files"
        is SendState.SendingFile -> "Sending Files"
        is SendState.TextSent -> "Text Sent"
        is SendState.FilesSent -> "Files Sent"
        is SendState.Failed -> "Could Not Send"
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
                        .clip(MaterialTheme.shapes.extraExtraLarge),
                    containerColor = MaterialTheme.colorScheme.surface,
                    // 4. Disable internal inset consumption so our custom modifiers control the shape
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationBarItem(
                        onClick = navigationViewModel::navigateToReceive,
                        selected = navigationViewModel.currentScreen() ==
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
                        onClick = navigationViewModel::navigateToSend,
                        selected = navigationViewModel.currentScreen() ==
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
                            modifier = Modifier,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            onClick = navigationViewModel::goBack
                        ) {
                            Icon(
                                imageVector = Back,
                                contentDescription = "Close settings"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = when (currentScreen) {
                            NavScreen.SendScreen -> sendTitle
                            NavScreen.ReceiveScreen -> receiveTitle
                            NavScreen.SettingsScreen -> "Settings"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
                actions = {
                    if (currentScreen != NavScreen.SettingsScreen) {
                        IconButton(
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                            onClick = navigationViewModel::navigateToSettings
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
        modifier = Modifier
            .fillMaxSize()
            .then(
            if (shouldKeepScreenOn) Modifier.keepScreenOn()
            else Modifier
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        NavDisplay(
            backStack = navigationViewModel.backstack,
            modifier = Modifier.padding(innerPadding),
            onBack = navigationViewModel::goBack,
            sceneStrategies = listOf(twoPaneStrategy)
        ) { screen ->
            when (screen) {
                is NavScreen.SendScreen -> {
                    NavEntry(
                        key = screen,
                        metadata = TwoPaneScene.firstPane()
                    ) {
                        SendScreen(
                            onTroubleshootClick = navigationViewModel::navigateToSettings
                        )
                    }
                }

                is NavScreen.ReceiveScreen -> {
                    NavEntry(
                        key = screen,
                        metadata = TwoPaneScene.secondPane()
                    ) {
                        ReceiveScreen(
                            onTroubleshootClick = navigationViewModel::navigateToSettings
                        )
                    }
                }

                is NavScreen.SettingsScreen -> {
                    NavEntry(key = screen) {
                        SettingsScreen(
                            repairEnabled = repairEnabled,
                            onRepairClick = sendScreenViewModel::repairNetworkServices
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(receiveScreenState) {
        if (
            receiveScreenState is ReceiveState.ReceivedText ||
            receiveScreenState is ReceiveState.ReceivingFiles
        ) {
            navigationViewModel.navigateToReceive()
        }
    }

    LaunchedEffect(useTwoPane) {
        navigationViewModel.setTwoPane(useTwoPane)
    }
}
