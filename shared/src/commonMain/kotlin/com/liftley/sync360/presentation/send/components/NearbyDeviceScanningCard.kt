package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liftley.sync360.domain.model.DiscoveryStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun NearbyDeviceScanningCard(
    status: DiscoveryStatus = DiscoveryStatus.Running,
    reloadEnabled: Boolean = false,
    onReloadClick: () -> Unit = {}
) {
    val title = when (status) {
        DiscoveryStatus.Idle -> "Scanning stopped"
        DiscoveryStatus.Starting -> "Starting discovery"
        DiscoveryStatus.Running -> "Looking for devices"
        DiscoveryStatus.Stopping -> "Stopping discovery"
    }

    val subtitle = when (status) {
        DiscoveryStatus.Idle -> {
            if (reloadEnabled) "Tap to rescan" else "Use connection repair in Settings"
        }
        DiscoveryStatus.Starting -> "Preparing nearby scan"
        DiscoveryStatus.Running -> "Keep both devices on the same Wi-Fi"
        DiscoveryStatus.Stopping -> "Cleaning up current scan"
    }

    Surface(
        onClick = onReloadClick,
        enabled = reloadEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when(status) {
                DiscoveryStatus.Starting -> LoadingIndicator()
                DiscoveryStatus.Stopping -> LoadingIndicator()
                else -> {}
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
