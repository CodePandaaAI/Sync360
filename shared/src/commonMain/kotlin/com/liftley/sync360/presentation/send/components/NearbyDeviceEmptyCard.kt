package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liftley.sync360.domain.model.DiscoveryStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun NearbyDeviceEmptyCard(
    status: DiscoveryStatus = DiscoveryStatus.Running,
    onReloadClick: () -> Unit = {}
) {
    val title = when (status) {
        DiscoveryStatus.Idle -> "No Device Found"
        DiscoveryStatus.Starting -> "Starting Discovery"
        DiscoveryStatus.Running -> "Looking for Devices"
        DiscoveryStatus.Stopping -> "Stopping Discovery"
    }

    val subtitle = when (status) {
        DiscoveryStatus.Idle -> "Tap reload to scan again"
        DiscoveryStatus.Starting -> "Preparing nearby scan"
        DiscoveryStatus.Running -> "Keep both devices on the same Wi-Fi"
        DiscoveryStatus.Stopping -> "Cleaning up current scan"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(
                enabled = status == DiscoveryStatus.Idle,
                onClick = onReloadClick
            )
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (status != DiscoveryStatus.Idle) {
                CircularWavyProgressIndicator()
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