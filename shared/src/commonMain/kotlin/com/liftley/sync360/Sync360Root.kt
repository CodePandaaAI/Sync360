package com.liftley.sync360

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.presentation.navigation.NavScreen
import com.liftley.sync360.presentation.navigation.NavigationViewModel
import com.liftley.sync360.presentation.receive.ReceiveScreen
import com.liftley.sync360.presentation.receive.ReceiveScreenViewModel
import com.liftley.sync360.presentation.receive.model.ReceiveState
import com.liftley.sync360.presentation.send.SendScreen
import com.liftley.sync360.presentation.send.SendScreenViewModel
import com.liftley.sync360.presentation.send.model.SendState
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

    val useNavigationRail =
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    val shouldKeepScreenOn =
        sendScreenState.sendState !is SendState.Idle || receiveScreenState !is ReceiveState.Idle

    val isReceivingFiles = receiveScreenState is ReceiveState.ReceivingFiles
    val receivedText = receiveScreenState as? ReceiveState.ReceivedText

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
            if (!useNavigationRail) {
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
                        onClick = { navigationViewModel.navigateTo(NavScreen.ReceiveScreen) },
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
                        onClick = { navigationViewModel.navigateTo(NavScreen.SendScreen) },
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
                title = {
                    Text(
                        text = when (currentScreen) {
                            NavScreen.SendScreen -> sendTitle
                            NavScreen.ReceiveScreen -> receiveTitle
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp),
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (useNavigationRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    // Scaffold has already supplied the system-bar padding.
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationRailItem(
                        selected = currentScreen == NavScreen.ReceiveScreen,
                        onClick = { navigationViewModel.navigateTo(NavScreen.ReceiveScreen) },
                        icon = {
                            Icon(
                                imageVector = Download,
                                contentDescription = null
                            )
                        },
                        label = { Text("Receive") }
                    )

                    NavigationRailItem(
                        selected = currentScreen == NavScreen.SendScreen,
                        onClick = { navigationViewModel.navigateTo(NavScreen.SendScreen) },
                        icon = {
                            Icon(
                                imageVector = Send,
                                contentDescription = null
                            )
                        },
                        label = { Text("Send") }
                    )

                }
            }

            NavDisplay(
                backStack = navigationViewModel.backstack,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onBack = navigationViewModel::goBack
            ) { screen ->
                when (screen) {
                    NavScreen.SendScreen -> {
                        NavEntry(key = screen) {
                            SendScreen()
                        }
                    }

                    NavScreen.ReceiveScreen -> {
                        NavEntry(key = screen) {
                            ReceiveScreen()
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isReceivingFiles) {
        if (isReceivingFiles) {
            navigationViewModel.navigateTo(NavScreen.ReceiveScreen)
        }
    }

    LaunchedEffect(receivedText) {
        if (receivedText != null) {
            navigationViewModel.navigateTo(NavScreen.ReceiveScreen)
        }
    }
}
