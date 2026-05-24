package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.components.NetworkStatusPill
import com.liftley.sync360.features.sync.presentation.components.StreamSections

@Composable
fun DesktopDashboard(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeDevice = uiState.connectedDevices.firstOrNull { it.id == uiState.activeDeviceId }
    val activeStream = uiState.activeDeviceId?.let { uiState.deviceStreams[it] }
    var showPairingDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        DesktopDeviceRail(
            uiState = uiState,
            onEvent = onEvent,
            onAddDevice = { showPairingDialog = true },
            modifier = Modifier
                .width(304.dp)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            DesktopHeader(uiState = uiState)
            StreamSections(
                stream = activeStream,
                activeDevice = activeDevice,
                onCopyClipboard = {
                    uiState.activeDeviceId?.let { onEvent(SyncEvent.CopyClipboard(it)) }
                },
                onDownload = { onEvent(SyncEvent.RequestDownload(it)) },
                desktopMode = true
            )
        }
    }

    if (showPairingDialog) {
        PairDeviceDialog(
            uiState = uiState,
            onEvent = onEvent,
            onDismiss = { showPairingDialog = false }
        )
    }

    uiState.pendingPairingRequests.firstOrNull()?.let { request ->
        IncomingPairingDialog(
            device = request,
            onAccept = { onEvent(SyncEvent.AcceptPairing(request.id)) },
            onDecline = { onEvent(SyncEvent.DeclinePairing(request.id)) }
        )
    }

    uiState.proactivePromptDevice?.let { request ->
        ProactivePromptDialog(
            device = request,
            onAccept = { onEvent(SyncEvent.ConfirmProactiveConnect(request)) },
            onDecline = { onEvent(SyncEvent.DismissProactivePrompt) }
        )
    }
}

@Composable
private fun ProactivePromptDialog(
    device: DeviceProfile,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Connect to ${device.name}?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "This device is on your local network. Do you want to connect and sync with it?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onAccept, shape = CircleShape) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Not now")
            }
        }
    )
}

@Composable
private fun DesktopDeviceRail(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit,
    onAddDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colorScheme.surfaceContainerLow)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Sync360",
            style = MaterialTheme.typography.headlineSmall,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        NetworkStatusPill(
            connectionStatus = uiState.connectionStatus,
            localNetworkHealthy = uiState.localNetworkHealthy
        )

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.65f))

        Text(
            text = "Connected devices",
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.connectedDevices) { device ->
                DeviceRailItem(
                    device = device,
                    isActive = device.id == uiState.activeDeviceId,
                    onClick = { onEvent(SyncEvent.SwitchDevice(device.id)) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Host address", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                Text(uiState.serverIp, style = MaterialTheme.typography.bodyLarge, color = colorScheme.onSurface)
                Text("${uiState.clientCount} active clients", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
        }

        Button(
            onClick = onAddDevice,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            Text("Add new device")
        }
    }
}

@Composable
private fun PairDeviceDialog(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Add new device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Sync360 is advertising this desktop on the local network. On mobile, open Sync360 on the same Wi-Fi and select the discovered desktop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PairingRow("Host", uiState.serverIp)
                        PairingRow("Port", "8080")
                        PairingRow("Connected", uiState.clientCount.toString())
                    }
                }
                Text(
                    text = if (uiState.connectedDevices.isEmpty()) {
                        "Waiting for a phone or tablet to connect."
                    } else {
                        "${uiState.connectedDevices.size} device(s) already known."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                if (uiState.nearbyDevices.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Nearby",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        uiState.nearbyDevices.forEach { device ->
                            PairableDeviceRow(
                                device = device,
                                onClick = {
                                    onEvent(SyncEvent.PairWithDevice(device.id))
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun IncomingPairingDialog(
    device: DeviceProfile,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Connect to ${device.name}?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "This device is on your local network and wants to pair with Sync360. If you accept, it becomes the active device for clipboard sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onAccept, shape = CircleShape) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
private fun PairableDeviceRow(
    device: DeviceProfile,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = when (device.type) {
                DeviceType.DESKTOP -> "PC"
                DeviceType.PHONE -> "PH"
                DeviceType.TABLET -> "TB"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = device.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Connect",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PairingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DeviceRailItem(
    device: DeviceProfile,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isActive) colorScheme.primaryContainer else colorScheme.surfaceContainerLow
    val contentColor = if (isActive) colorScheme.onPrimaryContainer else colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isActive) colorScheme.primary else colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (device.type) {
                    DeviceType.DESKTOP -> "PC"
                    DeviceType.PHONE -> "PH"
                    DeviceType.TABLET -> "TB"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (device.isOnline) "Available" else "Offline",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun DesktopHeader(uiState: SyncUiState) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active stream viewer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Drag fully downloaded media or documents out of the window when native file export is wired.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            HeaderMetric("Devices", uiState.connectedDevices.size.toString())
            HeaderMetric("Storage", "${uiState.activeDeviceId?.let { uiState.deviceStreams[it]?.storageUsedPercent } ?: 0}%")
        }
    }
}

@Composable
private fun HeaderMetric(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 108.dp)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
