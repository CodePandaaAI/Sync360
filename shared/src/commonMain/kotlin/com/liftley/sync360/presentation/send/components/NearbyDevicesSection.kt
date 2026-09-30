package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearbyDevicesSection(
    screenState: SendScreenState,
    onStartNearbySharing: () -> Unit,
    onStopNearbySharing: () -> Unit,
    onRetryDiscovery: () -> Unit,
    onDeviceClick: (String) -> Unit
) {
    val hasDevices = screenState.nearbyDevices.isNotEmpty()
    val status = when (screenState.discoveryStatus) {
        DiscoveryStatus.Idle -> "Discovery is off"
        DiscoveryStatus.Starting -> "Starting discovery…"
        DiscoveryStatus.FailedToStart -> "Couldn’t start discovery"
        DiscoveryStatus.Running -> if (hasDevices) "searching for more devices…" else "Searching for nearby devices…"
        DiscoveryStatus.Stopping -> "Stopping discovery…"
        DiscoveryStatus.CleanupFailed -> "Couldn’t stop discovery"
    }
    val (buttonLabel, onButtonClick) = when (screenState.discoveryStatus) {
        DiscoveryStatus.Idle -> "Start" to onStartNearbySharing
        DiscoveryStatus.Starting,
        DiscoveryStatus.Running,
        DiscoveryStatus.Stopping -> "Stop" to onStopNearbySharing
        DiscoveryStatus.FailedToStart,
        DiscoveryStatus.CleanupFailed -> "Try again" to onRetryDiscovery
    }
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Sync360Surface(
            shape = MaterialTheme.shapes.large.copy(
                bottomStart = CornerSize(8.dp),
                bottomEnd = CornerSize(8.dp)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nearby devices",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedButton(onClick = onButtonClick) {
                    Text(buttonLabel)
                }
            }
        }

        if (hasDevices) {
            screenState.nearbyDevices.forEach { device ->
                NearbyDeviceRow(
                    shape = MaterialTheme.shapes.extraLarge.copy(
                        topStart = CornerSize(8.dp),
                        topEnd = CornerSize(8.dp),
                        bottomStart = CornerSize(8.dp),
                        bottomEnd = CornerSize(8.dp)
                    ),
                    device = device,
                    enabled = screenState.isContentReadyToSend,
                    actionLabel = screenState.deviceActionLabel,
                    onClick = { onDeviceClick(device.id) })
            }
            Sync360Surface(
                Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge.copy(
                    topStart = CornerSize(8.dp),
                    topEnd = CornerSize(8.dp),
                )
            ) {
                Spacer(Modifier.height(32.dp))
            }
            Spacer(modifier = Modifier)

            Sync360Surface {
                Row(
                    modifier = Modifier.padding(16.dp),
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
            }
        } else {
            Sync360Surface(
                shape = MaterialTheme.shapes.large.copy(
                    topStart = CornerSize(8.dp),
                    topEnd = CornerSize(8.dp)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                        text = when (screenState.discoveryStatus) {
                            DiscoveryStatus.Idle -> "Click Start to find nearby devices and let them find you."
                            DiscoveryStatus.FailedToStart,
                            DiscoveryStatus.CleanupFailed -> "Tap Try again to retry discovery."
                            else -> "Open Sync360 on the other device and connect both to the same Wi-Fi network or hotspot."
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NearbyDeviceRow(
    shape: CornerBasedShape,
    device: NearbyDeviceUiModel,
    enabled: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Icon(
                    imageVector = when (device.deviceType) {
                        "Android" -> Android
                        "Tv" -> Tv
                        else -> Desktop
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
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
