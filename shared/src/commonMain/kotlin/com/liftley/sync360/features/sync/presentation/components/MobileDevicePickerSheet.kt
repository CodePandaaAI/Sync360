package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.SyncUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileDevicePickerSheet(
    uiState: SyncUiState,
    onDismiss: () -> Unit,
    onSelectPaired: (String) -> Unit,
    onPairNearby: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Paired Devices Group
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paired Devices",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.connectedDevices.isEmpty()) {
                    Text(
                        text = "No paired devices yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    uiState.connectedDevices.forEach { device ->
                        DeviceRowItem(
                            device = device,
                            isSelected = device.id == uiState.activeDeviceId,
                            actionLabel = if (device.id == uiState.activeDeviceId) "Connected" else "Use",
                            onClick = { onSelectPaired(device.id) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Nearby Discovered Devices Group
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Nearby on Local Wi-Fi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                val nearbyFiltered = uiState.nearbyDevices.filterNot { nearby ->
                    uiState.connectedDevices.any { it.id == nearby.id }
                }
                if (nearbyFiltered.isEmpty()) {
                    Text(
                        text = "Searching for nearby devices…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    nearbyFiltered.forEach { device ->
                        DeviceRowItem(
                            device = device,
                            isSelected = false,
                            actionLabel = "Connect",
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
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) colorScheme.primary else colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (device.type) {
                        DeviceType.DESKTOP -> "PC"
                        DeviceType.PHONE -> "PH"
                        DeviceType.TABLET -> "TB"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSelected) "Active Connection" else if (device.isOnline) "Available" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) colorScheme.primary else colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
