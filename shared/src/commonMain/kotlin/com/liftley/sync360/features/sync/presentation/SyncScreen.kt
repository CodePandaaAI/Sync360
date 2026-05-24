package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.liftley.sync360.features.sync.presentation.components.StreamSections

@Composable
fun SyncScreen(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    val activeDevice = uiState.connectedDevices.firstOrNull { it.id == uiState.activeDeviceId }
    val activeStream = uiState.activeDeviceId?.let { uiState.deviceStreams[it] }
    var showDevicePicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MobileDeviceTopBar(
                activeDevice = activeDevice,
                onOpenDevicePicker = { showDevicePicker = true }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                if (activeDevice == null) {
                    NoSelectedDeviceCard(onChooseDevice = { showDevicePicker = true })
                } else {
                    Button(
                        onClick = { onEvent(SyncEvent.SendCurrentClipboard) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape
                    ) {
                        Text("Send current clipboard")
                    }
                    StreamSections(
                        stream = activeStream,
                        activeDevice = activeDevice,
                        onCopyClipboard = { onEvent(SyncEvent.CopyClipboard(activeDevice.id)) },
                        onDownload = { onEvent(SyncEvent.RequestDownload(it)) }
                    )
                }
            }
        }
    }

    if (showDevicePicker) {
        DevicePickerSheet(
            uiState = uiState,
            onDismiss = { showDevicePicker = false },
            onSelectPaired = {
                onEvent(SyncEvent.SwitchDevice(it))
                showDevicePicker = false
            },
            onPairNearby = {
                onEvent(SyncEvent.PairWithDevice(it))
                showDevicePicker = false
            }
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
    androidx.compose.material3.AlertDialog(
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
            androidx.compose.material3.TextButton(onClick = onDecline) {
                Text("Not now")
            }
        }
    )
}

@Composable
private fun MobileDeviceTopBar(
    activeDevice: DeviceProfile?,
    onOpenDevicePicker: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpenDevicePicker),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = activeDevice?.name ?: "Choose device",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoSelectedDeviceCard(onChooseDevice: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "No active device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Open Sync360 on your PC and choose it from nearby devices. Sync360 only fetches content from the device you select.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onChooseDevice, shape = CircleShape) {
                Text("Choose device")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePickerSheet(
    uiState: SyncUiState,
    onDismiss: () -> Unit,
    onSelectPaired: (String) -> Unit,
    onPairNearby: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            DeviceGroup(
                title = "Paired",
                emptyText = "No paired devices yet.",
                devices = uiState.connectedDevices,
                activeDeviceId = uiState.activeDeviceId,
                actionLabel = "Use",
                onClick = onSelectPaired
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            DeviceGroup(
                title = "Nearby",
                emptyText = "Searching on local network...",
                devices = uiState.nearbyDevices.filterNot { nearby ->
                    uiState.connectedDevices.any { it.id == nearby.id }
                },
                activeDeviceId = null,
                actionLabel = "Connect",
                onClick = onPairNearby
            )
        }
    }
}

@Composable
private fun DeviceGroup(
    title: String,
    emptyText: String,
    devices: List<DeviceProfile>,
    activeDeviceId: String?,
    actionLabel: String,
    onClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (devices.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devices.forEach { device ->
                    DevicePickerRow(
                        device = device,
                        selected = device.id == activeDeviceId,
                        actionLabel = actionLabel,
                        onClick = { onClick(device.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DevicePickerRow(
    device: DeviceProfile,
    selected: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) colorScheme.primary else colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (device.type) {
                    DeviceType.DESKTOP -> "PC"
                    DeviceType.PHONE -> "PH"
                    DeviceType.TABLET -> "TB"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (selected) "Active" else if (device.isOnline) "Available" else "Offline",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (selected) "Selected" else actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
