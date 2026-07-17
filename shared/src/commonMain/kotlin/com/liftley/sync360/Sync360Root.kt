package com.liftley.sync360

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.presentation.app.components.Sync360Surface
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
import org.koin.compose.koinInject

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun Sync360Root() {
    val navigationViewModel = koinInject<NavigationViewModel>()
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val sendScreenViewModel = koinInject<SendScreenViewModel>()

    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    val sendScreenState by sendScreenViewModel.screenState.collectAsStateWithLifecycle()

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
            if (!useTwoPane) {
                NavigationBar {
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
                title = {
                    Sync360Surface {
                        Text(
                            text = if (navigationViewModel.checkCurrentTop() == NavScreen.SendScreen) sendTitle else receiveTitle,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(16.dp)
                        )
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
                        SendScreen()
                    }
                }

                is NavScreen.ReceiveScreen -> {
                    NavEntry(
                        key = screen,
                        metadata = TwoPaneScene.secondPane()
                    ) {
                        ReceiveScreen()
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
