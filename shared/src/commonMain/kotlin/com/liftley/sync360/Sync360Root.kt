package com.liftley.sync360

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.presentation.featureNavigation.NavScreen
import com.liftley.sync360.presentation.featureReceive.ReceiveScreen
import com.liftley.sync360.presentation.featureSend.SendScreen
import com.liftley.sync360.presentation.viewmodel.NavigationViewModel
import org.koin.compose.koinInject

@Preview(showBackground = true)
@Composable
fun Sync360Root() {
    val navigationViewModel = koinInject<NavigationViewModel>()
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
            }
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
        }
    }
}