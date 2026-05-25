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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.components.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DesktopDashboard(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeDevice = uiState.activeDevice()
    val activeStream = uiState.activeDeviceId?.let { uiState.deviceStreams[it] }
    var copiedFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedFeedbackText) {
        if (copiedFeedbackText != null) {
            delay(1500.milliseconds)
            copiedFeedbackText = null
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            copiedFeedbackText = msg
            onEvent(SyncEvent.ClearUserMessage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                DesktopHero(activeDevice = activeDevice)

                SectionLabel("You'll appear as")
                DesktopIdentityCard(serverIp = uiState.serverIp)

                SectionLabel(if (activeDevice == null) "Ready to receive" else "Sharing with you")
                if (activeDevice == null) {
                    DesktopReadySurface(
                        isScanning = uiState.isScanningForDevices,
                        onScan = { onEvent(SyncEvent.TriggerScan) }
                    )
                } else {
                    uiState.incomingFileOffer?.let { offer ->
                        IncomingFileOfferCard(offer = offer, onEvent = onEvent)
                    }
                    uiState.receivedFileBatch?.let { batch ->
                        ReceivedFileBatchCard(batch = batch, onEvent = onEvent)
                    }
                    uiState.fileTransferProgress?.let { progress ->
                        FileTransferProgressCard(progress = progress)
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                DesktopDevicesPanel(
                    uiState = uiState,
                    activeDevice = activeDevice,
                    onEvent = onEvent
                )

                if (activeDevice != null) {
                    ClipboardHistorySection(
                        textsList = activeStream?.latestTexts ?: emptyList(),
                        onCopyClick = { clipboard ->
                            onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                            copiedFeedbackText = clipboard.text
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

    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun DesktopHero(activeDevice: DeviceProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Sync360",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.sp
        )
        Text(
            text = activeDevice?.let { "Connected with ${it.name}" }
                ?: "Choose a nearby device to share privately over your local network.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DesktopIdentityCard(serverIp: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface
    ) {
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
                Text("This desktop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
private fun DesktopReadySurface(
    isScanning: Boolean,
    onScan: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
            } else {
                Surface(shape = CircleShape, color = colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Text(
                text = if (isScanning) "Looking for nearby devices" else "Ready to receive",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Keep Sync360 open on your other device. Found devices appear in the panel on the right.",
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onScan,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan again", fontWeight = FontWeight.Bold)
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
    val pairedIds = uiState.connectedDevices.map { it.id }.toSet()
    val nearby = uiState.nearbyDevices.filter { it.id !in pairedIds }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (activeDevice == null) "Pick a nearby device" else "Connected session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { onEvent(SyncEvent.TriggerScan) },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (uiState.isScanningForDevices) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (uiState.connectedDevices.isNotEmpty()) {
                DeviceGroup("Paired this session") {
                    uiState.connectedDevices.forEach { device ->
                        DesktopDeviceListRow(
                            device = device,
                            selected = device.id == uiState.activeDeviceId,
                            action = if (device.id == uiState.activeDeviceId) "Active" else "Use",
                            onClick = { onEvent(SyncEvent.SwitchDevice(device.id)) }
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

            DeviceGroup("Nearby devices") {
                if (nearby.isEmpty()) {
                    Surface(shape = RoundedCornerShape(22.dp), color = colorScheme.surfaceContainer) {
                        Text(
                            text = if (uiState.isScanningForDevices) "Searching local network..." else "No nearby devices found.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
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

            if (activeDevice != null) {
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.Disconnect) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
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
                color = colorScheme.primary
            )
        }
    }
}

private fun deviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}
