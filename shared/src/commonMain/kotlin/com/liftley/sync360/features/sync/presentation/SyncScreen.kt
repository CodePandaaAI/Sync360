package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    val activeDevice = uiState.activeDevice()
    val activeStream = uiState.activeDeviceId?.let { uiState.deviceStreams[it] }
    var showDevicePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pairedIds = uiState.connectedDevices.map { it.id }.toSet()
    val visibleNearby = uiState.nearbyDevices.filter { it.id !in pairedIds }

    LaunchedEffect(uiState.activeDeviceId) {
        if (uiState.activeDeviceId == null) {
            showDevicePicker = true
        }
    }

    var copiedFeedbackTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(copiedFeedbackTrigger) {
        if (copiedFeedbackTrigger > 0) {
            snackbarHostState.showSnackbar("Copied to clipboard")
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onEvent(SyncEvent.ClearUserMessage)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            SyncTopBar(
                uiState = uiState,
                activeDevice = activeDevice,
                nearbyCount = visibleNearby.size,
                isScanning = uiState.isScanningForDevices,
                onOpenDevices = {
                    if (!uiState.isScanningForDevices) onEvent(SyncEvent.TriggerScan)
                    showDevicePicker = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = Spacing.md,
                vertical = Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            item {
                Text(
                    text = "Sync360",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item {
                Text(
                    text = "You'll appear as",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LocalDeviceCard(serverIp = uiState.serverIp)
            }

            item {
                Text(
                    text = if (activeDevice == null) "Ready to receive" else "Sharing with you",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (activeDevice == null) {
                item {
                    ReadyReceiveSurface(
                        isScanning = uiState.isScanningForDevices,
                        onOpenDevices = { showDevicePicker = true }
                    )
                }
            } else {
                uiState.incomingFileOffer?.let { offer ->
                    item {
                        IncomingFileOfferCard(
                            offer = offer,
                            onEvent = onEvent
                        )
                    }
                }

                uiState.receivedFileBatch?.let { batch ->
                    item {
                        ReceivedFileBatchCard(
                            batch = batch,
                            onEvent = onEvent
                        )
                    }
                }

                uiState.fileTransferProgress?.let { progress ->
                    item {
                        FileTransferProgressCard(progress = progress)
                    }
                }

                item {
                    SharePanel(
                        isDesktop = false,
                        uiState = uiState,
                        activeDevice = activeDevice,
                        onEvent = onEvent
                    )
                }

                item {
                    ClipboardHistorySection(
                        textsList = activeStream?.latestTexts ?: emptyList(),
                        onCopyClick = { _ ->
                            onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                            copiedFeedbackTrigger++
                        }
                    )
                }
            }
        }
    }

    if (showDevicePicker) {
        MobileDevicePickerSheet(
            uiState = uiState,
            onDismiss = { showDevicePicker = false },
            onSelectPaired = {
                onEvent(SyncEvent.SwitchDevice(it))
                showDevicePicker = false
            },
            onPairNearby = {
                onEvent(SyncEvent.RequestConnect(it))
                showDevicePicker = false
            },
            onDisconnect = { onEvent(SyncEvent.Disconnect) }
        )
    }

    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncTopBar(
    uiState: SyncUiState,
    activeDevice: DeviceProfile?,
    nearbyCount: Int,
    isScanning: Boolean,
    onOpenDevices: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    CenterAlignedTopAppBar(
        title = {
            DevicePill(
                uiState = uiState,
                activeDevice = activeDevice,
                nearbyCount = nearbyCount,
                isScanning = isScanning,
                onClick = onOpenDevices
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainer,
            titleContentColor = colorScheme.onBackground
        )
    )
}

@Composable
private fun DevicePill(
    uiState: SyncUiState,
    activeDevice: DeviceProfile?,
    nearbyCount: Int,
    isScanning: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val label = when {
        activeDevice != null -> activeDevice.name
        isScanning -> "Scanning"
        nearbyCount > 0 -> "$nearbyCount nearby"
        else -> "Nearby devices"
    }
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isScanning && activeDevice == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = activeDevice?.type?.let { deviceIcon(it) } ?: Icons.Default.Wifi,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LocalDeviceCard(serverIp: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("This device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = serverIp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReadyReceiveSurface(
    isScanning: Boolean,
    onOpenDevices: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDevices),
        shape = RoundedCornerShape(28.dp),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(vertical = 44.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp,
                    color = colorScheme.primary
                )
            } else {
                Surface(shape = CircleShape, color = colorScheme.primaryContainer, modifier = Modifier.size(58.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = colorScheme.primary)
                    }
                }
            }
            Text(
                text = if (isScanning) "Looking for nearby devices" else "Ready to receive",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = "Tap the device pill to choose who to connect with.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deviceIcon(type: DeviceType): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}
