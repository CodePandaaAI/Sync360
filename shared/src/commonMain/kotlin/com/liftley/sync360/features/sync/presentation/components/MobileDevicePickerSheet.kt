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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.core.designsystem.SyncDimens
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.SyncUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDevicePickerSheet(
    uiState: SyncUiState,
    onDismiss: () -> Unit,
    onPairNearby: (String) -> Unit,
    onManualConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit
) {
    var manualHost by remember { mutableStateOf("") }
    val activeDevice = uiState.activeDevice
    val nearbyOnly = uiState.nearbyDevices.filter { it.id != activeDevice?.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = SyncDimens.cornerMedium, topEnd = SyncDimens.cornerMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (activeDevice == null) "Nearby devices" else "Connected device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (activeDevice != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "This session",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    DeviceRowItem(
                        device = activeDevice,
                        isSelected = true,
                        actionLabel = "Connected",
                        enabled = false,
                        onClick = {}
                    )
                }
                OutlinedButton(
                    onClick = {
                        onDisconnect()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby on Local Wi-Fi",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    if (uiState.isScanningForDevices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Scan again",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onScan)
                        )
                    }
                }
                if (nearbyOnly.isEmpty()) {
                    Text(
                        text = if (uiState.isScanningForDevices) "Searching for nearby devices..." else "Scan stopped. No devices found.",
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Connect by IP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("IP address") },
                    shape = RoundedCornerShape(24.dp)
                )
                Button(
                    onClick = {
                        onManualConnect(manualHost)
                        onDismiss()
                    },
                    enabled = manualHost.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.SemiBold)
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
    Sync360Surface(
        modifier = Modifier
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        cornerRadius = 24.dp,
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
