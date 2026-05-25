package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
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
                nearbyCount = visibleNearby.size,
                isScanning = uiState.isScanningForDevices,
                onRefreshScan = { onEvent(SyncEvent.TriggerScan) }
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
                NearbyDevicesSection(
                    title = "Nearby devices",
                    nearbyDevices = uiState.nearbyDevices,
                    pairedDeviceIds = pairedIds,
                    isScanning = uiState.isScanningForDevices,
                    localIp = uiState.serverIp,
                    onConnect = { onEvent(SyncEvent.RequestConnect(it)) },
                    onRefresh = { onEvent(SyncEvent.TriggerScan) }
                )
            }

            if (uiState.connectedDevices.isNotEmpty()) {
                item {
                    Text(
                        text = "Paired this session",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                    )
                }
                items(uiState.connectedDevices, key = { it.id }) { device ->
                    val isActive = device.id == uiState.activeDeviceId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(SyncEvent.SwitchDevice(device.id)) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                        tonalElevation = if (isActive) 2.dp else 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = deviceIcon(device.type),
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isActive) "Active session partner" else "Standby pairing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isActive) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (activeDevice == null) {
                item {
                    ReadyToSyncCard(
                        isDesktop = false,
                        onChooseDevice = { showDevicePicker = true }
                    )
                }
            } else {
                item {
                    SharePanel(
                        isDesktop = false,
                        uiState = uiState,
                        activeDevice = activeDevice,
                        onEvent = onEvent
                    )
                }

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

                item {
                    ClipboardHistorySection(
                        textsList = activeStream?.latestTexts ?: emptyList(),
                        onCopyClick = { _ ->
                            onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                            copiedFeedbackTrigger++
                        }
                    )
                }

                // Disconnect Controls placed perfectly at the very bottom
                item {
                    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                        shape = RoundedCornerShape(24.dp),
                        color = if (isConnected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isConnected) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (isConnected) "Connected with ${activeDevice.name}" else "Standby - waiting for ${activeDevice.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                            }
                            
                            Button(
                                onClick = { onEvent(SyncEvent.Disconnect) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isConnected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error,
                                    contentColor = if (isConnected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isConnected) "Disconnect" else "Cancel Standby",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
            }
        )
    }

    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncTopBar(
    nearbyCount: Int,
    isScanning: Boolean,
    onRefreshScan: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    TopAppBar(
        title = {
            Text(
                text = "Sync360",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        },
        actions = {
            ScanningStatusChip(
                nearbyCount = nearbyCount,
                isScanning = isScanning,
                onRefresh = onRefreshScan,
                modifier = Modifier.padding(end = Spacing.md)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainer,
            titleContentColor = colorScheme.onBackground
        )
    )
}

private fun deviceIcon(type: DeviceType): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}
