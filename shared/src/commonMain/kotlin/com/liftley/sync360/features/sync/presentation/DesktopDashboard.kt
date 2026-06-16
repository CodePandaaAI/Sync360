package com.liftley.sync360.features.sync.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DesktopDashboard(
    uiState: SyncUiState,
    uiEffects: Flow<SyncUiEffect>,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeDevice = uiState.activeDevice
    var copiedFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedFeedbackText) {
        if (copiedFeedbackText != null) {
            delay(1500.milliseconds)
            copiedFeedbackText = null
        }
    }

    LaunchedEffect(uiEffects) {
        uiEffects.collect { effect ->
            when (effect) {
                is SyncUiEffect.ShowMessage -> copiedFeedbackText = effect.message
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.surfaceContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DesktopHero(activeDevice = activeDevice)

                if (activeDevice == null) {
                    SectionLabel("You'll appear as")
                    DesktopIdentityCard(serverIp = uiState.serverIp)
                    RuntimeSecurityBanner(
                        runtime = uiState.runtimeState,
                        securityMode = uiState.securityMode
                    )
                }

                SectionLabel(if (activeDevice == null) "Ready to receive" else "Sharing with you")
                if (activeDevice == null) {
                    ReadyTransferHomeCard(
                        isScanning = uiState.isScanningForDevices,
                        nearbyDevices = uiState.nearbyDevices,
                        onDeviceClick = { onEvent(SyncEvent.RequestConnect(it)) },
                        onScan = { onEvent(SyncEvent.TriggerScan) },
                        onOpenDevices = { onEvent(SyncEvent.TriggerScan) }
                    )
                } else {
                    if (
                        uiState.fileTransferProgress != null ||
                        uiState.fileTransferFailure != null ||
                        uiState.receivedFileBatch != null
                    ) {
                        TransferLifecycleCard(
                            progress = uiState.fileTransferProgress,
                            receivedBatch = uiState.receivedFileBatch,
                            failure = uiState.fileTransferFailure,
                            onEvent = onEvent
                        )
                    }
                    SharePanel(
                        isDesktop = true,
                        uiState = uiState,
                        activeDevice = activeDevice,
                        onEvent = onEvent
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 460.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DesktopDevicesPanel(
                    uiState = uiState,
                    activeDevice = activeDevice,
                    onEvent = onEvent
                )

                if (activeDevice != null) {
                    ClipboardHistorySection(
                        textsList = uiState.latestTexts,
                        onCopyClick = { _ ->
                            onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = copiedFeedbackText != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colorScheme.inverseSurface,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = copiedFeedbackText ?: "",
                    color = colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun DesktopHero(activeDevice: DeviceProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sync360TopBarTitle(title = "Sync360")
        Text(
            text = activeDevice?.let { "Connected with ${it.name}" }
                ?: "Choose a nearby device or connect by IP.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun DesktopIdentityCard(serverIp: String) {
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface(cornerRadius = 24.dp) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("This desktop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Visible on local Wi-Fi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = CircleShape, color = colorScheme.surfaceContainer) {
                Text(
                    text = serverIp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DesktopDevicesPanel(
    uiState: SyncUiState,
    activeDevice: DeviceProfile?,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var manualHost by remember { mutableStateOf("") }
    val nearby = uiState.nearbyDevices.filter { it.id != activeDevice?.id }

    Sync360Surface(cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (activeDevice == null) "Pick a nearby device" else "Connected session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
                if (uiState.isScanningForDevices) {
                    Sync360Surface(
                        modifier = Modifier.size(48.dp),
                        cornerRadius = 100.dp,
                        fillMaxWidth = false,
                        color = colorScheme.surface
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Sync360IconButton(
                        onClick = { onEvent(SyncEvent.TriggerScan) },
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan again",
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    )
                }
            }

            if (activeDevice != null) {
                DeviceGroup("This session") {
                    DesktopDeviceListRow(
                        device = activeDevice,
                        selected = true,
                        action = "Active",
                        onClick = {}
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.Disconnect) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            } else {
                DeviceGroup("Nearby devices") {
                    if (nearby.isEmpty()) {
                        Sync360Surface(cornerRadius = 24.dp, color = colorScheme.surfaceContainer) {
                            Text(
                                text = if (uiState.isScanningForDevices) "Searching local network..." else "No nearby devices found.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface
                            )
                        }
                    } else {
                        nearby.forEach { device ->
                            DesktopDeviceListRow(
                                device = device,
                                selected = false,
                                action = "Connect",
                                onClick = { onEvent(SyncEvent.RequestConnect(device.id)) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                DeviceGroup("Connect by IP") {
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
                            onEvent(SyncEvent.RequestConnectByHost(manualHost))
                            manualHost = ""
                        },
                        enabled = manualHost.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun DesktopDeviceListRow(
    device: DeviceProfile,
    selected: Boolean,
    action: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        cornerRadius = 24.dp,
        color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = if (selected) colorScheme.primary else colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = deviceIcon(device.type),
                        contentDescription = null,
                        tint = if (selected) colorScheme.onPrimary else colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        selected -> "Active connection"
                        device.isOnline -> "Available"
                        else -> device.hostAddress ?: "Offline"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.primary
            )
        }
    }
}

private fun deviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}
