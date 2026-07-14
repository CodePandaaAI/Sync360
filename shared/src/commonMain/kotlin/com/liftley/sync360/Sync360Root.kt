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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.navigation.NavScreen
import com.liftley.sync360.presentation.navigation.NavigationViewModel
import com.liftley.sync360.presentation.receive.ReceiveScreen
import com.liftley.sync360.presentation.receive.ReceiveScreenViewModel
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import com.liftley.sync360.presentation.send.SendScreen
import com.liftley.sync360.presentation.send.SendScreenViewModel
import com.liftley.sync360.presentation.send.model.SendOperationState
import org.koin.compose.koinInject

@Preview(showBackground = true)
@Composable
fun Sync360Root() {
    val navigationViewModel = koinInject<NavigationViewModel>()
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val sendScreenViewModel = koinInject<SendScreenViewModel>()

    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    val sendScreenState by sendScreenViewModel.screenState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    onClick = { navigationViewModel.addScreen(NavScreen.ReceiveScreen) },
                    selected = navigationViewModel.checkCurrentTop() == NavScreen.ReceiveScreen,
                    label = { Text("Receive") },
                    icon = {
                        Icon(
                            imageVector = Download,
                            contentDescription = null
                        )
                    }
                )
                NavigationBarItem(
                    onClick = { navigationViewModel.removeAllExceptAddScreen() },
                    selected = navigationViewModel.checkCurrentTop() == NavScreen.SendScreen,
                    label = { Text("Send") },
                    icon = {
                        Icon(
                            imageVector = Send,
                            contentDescription = null
                        )
                    }
                )
            }
        },
        topBar = {
            if (
                navigationViewModel.checkCurrentTop() ==
                NavScreen.ReceiveScreen
            ) {
                val topBarTitle = when (receiveScreenState) {
                    ReceiveScreenState.Idle -> "Receive"

                    is ReceiveScreenState.IncomingTextOffer ->
                        "Incoming text"

                    is ReceiveScreenState.IncomingFileOffer ->
                        "Incoming files"

                    is ReceiveScreenState.ReceivingFiles ->
                        "Receiving files"

                    is ReceiveScreenState.ReceivedText ->
                        "Received text"

                    is ReceiveScreenState.ReceivedFiles ->
                        "Files received"
                }

                CenterAlignedTopAppBar(
                    title = {
                        Sync360Surface {
                            Text(
                                text = topBarTitle,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }

            if (
                navigationViewModel.checkCurrentTop() ==
                NavScreen.SendScreen
            ) {
                val topBarTitle = when (sendScreenState.sendOperationState) {
                    SendOperationState.Idle -> "Sync360"

                    SendOperationState.Cancelled -> "Sending Cancelled"

                    is SendOperationState.SendingTextOffer -> {
                        "Sending Text Offer"
                    }

                    is SendOperationState.SendingFileOffer -> {
                        "Sending File Offer"
                    }

                    is SendOperationState.SendingFile -> {
                        "Sending Files"
                    }

                    is SendOperationState.TextSent -> {
                        "Text Sent"
                    }

                    is SendOperationState.FilesSent -> {
                        "Files Sent"
                    }

                    is SendOperationState.OperationFailed -> {
                        "Could Not Send"
                    }
                }

                CenterAlignedTopAppBar(
                    title = {
                        Sync360Surface {
                            Text(
                                text = topBarTitle,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) { innerPadding ->
        NavDisplay(
            navigationViewModel.backstack,
            modifier = Modifier.padding(innerPadding)
        ) { screen ->
            when (screen) {
                is NavScreen.SendScreen -> {
                    NavEntry(screen) {
                        SendScreen()
                    }
                }

                is NavScreen.ReceiveScreen -> {
                    NavEntry(screen) {
                        ReceiveScreen()
                    }
                }
            }
        }

        LaunchedEffect(receiveScreenState) {
            if (receiveScreenState is ReceiveScreenState.IncomingTextOffer) {
                navigationViewModel.addScreen(NavScreen.ReceiveScreen)
            }

            if (receiveScreenState is ReceiveScreenState.IncomingFileOffer) {
                navigationViewModel.addScreen(NavScreen.ReceiveScreen)
            }
        }
    }
}
