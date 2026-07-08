package com.liftley.sync360

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.presentation.navigation.NavScreen
import com.liftley.sync360.presentation.navigation.NavigationViewModel
import com.liftley.sync360.presentation.receive.ReceiveScreen
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import com.liftley.sync360.presentation.send.SendScreen
import com.liftley.sync360.presentation.receive.ReceiveScreenViewModel
import org.koin.compose.koinInject

@Preview(showBackground = true)
@Composable
fun Sync360Root() {
    val navigationViewModel = koinInject<NavigationViewModel>()
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()

    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    Sync360Theme {
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
                    if (navigationViewModel.checkCurrentTop() != NavScreen.ReceiveScreen) {
                        navigationViewModel.addScreen(NavScreen.ReceiveScreen)
                    }
                }

                if (receiveScreenState is ReceiveScreenState.IncomingFileOffer) {
                    if (navigationViewModel.checkCurrentTop() != NavScreen.ReceiveScreen) {
                        navigationViewModel.addScreen(NavScreen.ReceiveScreen)
                    }
                }
            }
        }
    }
}