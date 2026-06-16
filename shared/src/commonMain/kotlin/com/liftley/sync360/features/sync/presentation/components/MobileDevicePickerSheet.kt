package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Sync360TopBarTitle(
                    title = if (activeDevice == null) "Connect" else "Connected",
                    modifier = Modifier
                )
                if (uiState.isScanningForDevices) {
                    Sync360Surface(
                        modifier = Modifier.size(48.dp),
                        cornerRadius = 100.dp,
                        color = MaterialTheme.colorScheme.surface,
                        fillMaxWidth = false
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Sync360IconButton(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan again",
                        onClick = onScan
                    )
                }
            }

            if (activeDevice != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SheetSectionLabel("This session")
                    DeviceRowItem(
                        device = activeDevice,
                        isSelected = true,
                        actionLabel = "Connected",
                        enabled = false,
                        topRounding = 24.dp,
                        bottomRounding = 24.dp,
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
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.SemiBold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby devices",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.isScanningForDevices) {
                        Text(
                            text = "Scanning",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
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
                    Sync360Surface(
                        modifier = Modifier.clickable(onClick = onScan),
                        color = MaterialTheme.colorScheme.surface,
                        cornerRadius = 24.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.isScanningForDevices) "Searching..." else "No devices found. Scan again",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    nearbyOnly.forEachIndexed { index, device ->
                        DeviceRowItem(
                            device = device,
                            isSelected = false,
                            actionLabel = "Connect",
                            enabled = true,
                            topRounding = if (index == 0) 24.dp else 6.dp,
                            bottomRounding = if (index == nearbyOnly.lastIndex) 24.dp else 6.dp,
                            onClick = { onPairNearby(device.id) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SheetSectionLabel("Connect by IP")
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
    topRounding: androidx.compose.ui.unit.Dp,
    bottomRounding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(
            topStart = topRounding,
            topEnd = topRounding,
            bottomStart = bottomRounding,
            bottomEnd = bottomRounding
        ),
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold
    )
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
