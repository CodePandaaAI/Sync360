package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.SessionSecurityMode
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState

@Composable
fun RuntimeSecurityBanner(
    runtime: SyncRuntimeState,
    securityMode: SessionSecurityMode
) {
    val runtimeMessage = when (runtime) {
        SyncRuntimeState.Stopped -> "Sharing is stopped."
        SyncRuntimeState.Starting -> "Starting local sharing..."
        is SyncRuntimeState.Ready -> "Ready on your local network."
        is SyncRuntimeState.Degraded -> "Manual IP connection is available; discovery is limited."
        is SyncRuntimeState.Unavailable -> "Local sharing is unavailable."
        SyncRuntimeState.Stopping -> "Stopping local sharing..."
    }
    val securityMessage = when (securityMode) {
        SessionSecurityMode.TRUSTED_LAN_PLAINTEXT ->
            "Transfers use authenticated HTTP but are not encrypted. Use only your private home network or personal hotspot."
    }
    val unsafeNetworkMessage =
        "Do not use on cafe, hotel, airport, school, office, or other shared networks."

    Sync360Surface(cornerRadius = 20.dp, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = runtimeMessage,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Trusted network mode",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "$securityMessage $unsafeNetworkMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
