package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.liftley.sync360.features.sync.presentation.navigation.SyncRoute

@Composable
fun SyncBottomNavigationBar(
    currentRoute: SyncRoute,
    onRouteSelected: (SyncRoute) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == SyncRoute.Send,
            onClick = { onRouteSelected(SyncRoute.Send) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            },
            label = { Text("Send") }
        )
        NavigationBarItem(
            selected = currentRoute == SyncRoute.Receive,
            onClick = { onRouteSelected(SyncRoute.Receive) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = "Receive"
                )
            },
            label = { Text("Receive") }
        )
    }
}
