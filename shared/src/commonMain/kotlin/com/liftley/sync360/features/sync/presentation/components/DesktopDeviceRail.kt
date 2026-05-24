package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.presentation.SyncUiState
import com.liftley.sync360.features.sync.presentation.SyncEvent

@Composable
fun DesktopDeviceRail(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Sync360",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )
            Surface(
                shape = CircleShape,
                color = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                    colorScheme.primaryContainer 
                else colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wifi Network Indicator",
                        tint = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                            colorScheme.primary 
                        else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) "Hosting Local" else "Standby",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                            colorScheme.onPrimaryContainer 
                        else colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Discovered / Paired Devices
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Paired
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PAIRED DEVICES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant
                )
                if (uiState.connectedDevices.isEmpty()) {
                    Text(
                        text = "No paired devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    uiState.connectedDevices.forEach { device ->
                        DesktopDeviceRow(
                            device = device,
                            isActive = device.id == uiState.activeDeviceId,
                            actionLabel = if (device.id == uiState.activeDeviceId) "Connected" else "Use",
                            onClick = { onEvent(SyncEvent.SwitchDevice(device.id)) }
                        )
                    }
                }
            }

            // Discovered
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NEARBY DISCOVERED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurfaceVariant
                )
                val nearbyFiltered = uiState.nearbyDevices.filterNot { nearby ->
                    uiState.connectedDevices.any { it.id == nearby.id }
                }
                if (nearbyFiltered.isEmpty()) {
                    Text(
                        text = "Searching local network…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    nearbyFiltered.forEach { device ->
                        DesktopDeviceRow(
                            device = device,
                            isActive = false,
                            actionLabel = "Connect",
                            onClick = { onEvent(SyncEvent.RequestConnect(device.id)) }
                        )
                    }
                }
            }
        }

        // Host IP Address display
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("IP Address", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Text(uiState.serverIp, style = MaterialTheme.typography.titleMedium, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${uiState.clientCount} clients connected", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DesktopDeviceRow(
    device: DeviceProfile,
    isActive: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isActive) colorScheme.primary else colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = "Device Type",
                    tint = if (isActive) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isActive) "Active" else if (device.isOnline) "Available" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
