package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.core.designsystem.SyncDimens
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.SyncUiState

private fun isActivelyConnected(device: DeviceProfile, uiState: SyncUiState): Boolean {
    if (uiState.connectionStatus != ConnectionStatus.CONNECTED) return false
    if (device.id == uiState.activeDeviceId) return true
    val activeId = uiState.activeDeviceId ?: return false
    val activeDevice = uiState.connectedDevices.firstOrNull { it.id == activeId }
        ?: uiState.nearbyDevices.firstOrNull { it.id == activeId }
    if (activeDevice == null) return false
    return device.id == activeDevice.id ||
        (!device.connectionHost.isNullOrBlank() && device.connectionHost == activeDevice.connectionHost)
}

private fun isPairedDevice(device: DeviceProfile, uiState: SyncUiState): Boolean {
    return uiState.connectedDevices.any { it.id == device.id }
}

private fun deviceActionLabel(device: DeviceProfile, uiState: SyncUiState): String {
    return when {
        isActivelyConnected(device, uiState) -> "Connected"
        isPairedDevice(device, uiState) -> "Use"
        else -> "Connect"
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDevicePickerSheet(
    uiState: SyncUiState,
    onDismiss: () -> Unit,
    onSelectPaired: (String) -> Unit,
    onPairNearby: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val pairedIds = uiState.connectedDevices.map { it.id }.toSet()
    val connectedDevices = uiState.connectedDevices
    val nearbyOnly = uiState.nearbyDevices.filter { it.id !in pairedIds }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = SyncDimens.cornerMedium, topEnd = SyncDimens.cornerMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = if (uiState.activeDeviceId == null) "Nearby devices" else "Connected device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (connectedDevices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Paired Devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    connectedDevices.forEach { device ->
                        val activelyConnected = isActivelyConnected(device, uiState)
                        DeviceRowItem(
                            device = device,
                            isSelected = activelyConnected,
                            actionLabel = deviceActionLabel(device, uiState),
                            enabled = !activelyConnected,
                            onClick = {
                                if (activelyConnected) return@DeviceRowItem
                                if (isPairedDevice(device, uiState)) {
                                    onSelectPaired(device.id)
                                } else {
                                    onPairNearby(device.id)
                                }
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        onDisconnect()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(SyncDimens.cornerSmall),
                    contentPadding = PaddingValues(vertical = Spacing.sm + Spacing.xs)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    text = "Open Sync360 on another device connected to the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Nearby on Local Wi-Fi",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (nearbyOnly.isEmpty()) {
                    Text(
                        text = "Searching for nearby devices…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    nearbyOnly.forEach { device ->
                        DeviceRowItem(
                            device = device,
                            isSelected = false,
                            actionLabel = "Connect",
                            enabled = true,
                            onClick = { onPairNearby(device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRowItem(
    device: DeviceProfile,
    isSelected: Boolean,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SyncDimens.cornerMedium))
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(SyncDimens.cornerMedium),
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                modifier = Modifier.size(SyncDimens.touchTarget * 0.75f),
                shape = CircleShape,
                color = if (isSelected) colorScheme.primary else colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = deviceTypeIcon(device.type),
                        contentDescription = deviceTypeContentDescription(device.type),
                        tint = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.lg)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isSelected -> "Active connection"
                        device.isOnline -> "Available"
                        else -> "Offline"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    } else {
                        colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) colorScheme.primary else colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun deviceTypeIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}

private fun deviceTypeContentDescription(type: DeviceType): String = when (type) {
    DeviceType.DESKTOP -> "Desktop device"
    DeviceType.PHONE -> "Phone device"
    DeviceType.TABLET -> "Tablet device"
}
