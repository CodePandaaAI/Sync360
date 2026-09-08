package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Android
import com.liftley.sync360.core.designsystem.icons.Desktop
import com.liftley.sync360.core.designsystem.icons.Tv
import com.liftley.sync360.core.designsystem.icons.Wifi
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.NearbyDeviceUiModel
import com.liftley.sync360.presentation.send.model.SendScreenState

@Composable
fun NearbyDevicesSection(
    screenState: SendScreenState,
    onDiscoveryEnabledChange: (Boolean) -> Unit,
    onRetryDiscovery: () -> Unit,
    onDeviceClick: (String) -> Unit
) {
    val hasDevices = screenState.nearbyDevices.isNotEmpty()
    val status = when (screenState.discoveryStatus) {
        DiscoveryStatus.Idle -> "Discovery is off"
        DiscoveryStatus.Starting -> "Starting discovery…"
        DiscoveryStatus.Running -> if (hasDevices) "searching for more devices…" else "Searching for nearby devices…"
        DiscoveryStatus.Stopping -> "Stopping discovery…"
        DiscoveryStatus.CleanupFailed -> "Couldn’t stop discovery"
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Nearby devices",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge
        )
        if (screenState.isDiscoveryEnabled || hasDevices) {
            OutlinedButton(onClick = { onDiscoveryEnabledChange(!screenState.isDiscoveryEnabled) }) {
                Text(if (screenState.isDiscoveryEnabled) "Stop" else "Start")
            }
        }
    }

    if (hasDevices) {
        screenState.nearbyDevices.forEach { device ->
            NearbyDeviceRow(
                device = device,
                enabled = screenState.isContentReadyToSend,
                actionLabel = screenState.deviceActionLabel,
                onClick = { onDeviceClick(device.id) })
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (screenState.discoveryStatus == DiscoveryStatus.Running) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (screenState.discoveryErrorMessage == null) {
        Sync360Surface(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Sync360Surface(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Wifi,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    status,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (screenState.isDiscoveryEnabled) {
                        "Open Sync360 on the other device and connect both to the same Wi-Fi network or hotspot."
                    } else {
                        "Click Start discovery to find nearby devices and let them find you."
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!screenState.isDiscoveryEnabled) {
                    Button(onClick = { onDiscoveryEnabledChange(true) }) { Text("Start discovery") }
                }
            }
        }
    }

    screenState.discoveryErrorMessage?.let { message ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetryDiscovery) { Text("Try again") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NearbyDeviceRow(
    device: NearbyDeviceUiModel,
    enabled: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    imageVector = when (device.deviceType) {
                        "Android" -> Android
                        "Tv" -> Tv
                        else -> Desktop
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(device.deviceName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${device.deviceType} · $actionLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}