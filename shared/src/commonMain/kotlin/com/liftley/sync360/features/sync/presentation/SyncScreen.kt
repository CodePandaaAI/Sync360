package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.components.ClipboardHistorySection
import com.liftley.sync360.features.sync.presentation.components.ConfirmDialogs
import com.liftley.sync360.features.sync.presentation.components.MobileDevicePickerSheet
import com.liftley.sync360.features.sync.presentation.components.QuickSaveCard
import com.liftley.sync360.features.sync.presentation.components.ReadyTransferHomeCard
import com.liftley.sync360.features.sync.presentation.components.RestartSharingCard
import com.liftley.sync360.features.sync.presentation.components.RuntimeSecurityBanner
import com.liftley.sync360.features.sync.presentation.components.SharePanel
import com.liftley.sync360.features.sync.presentation.components.Sync360Surface
import com.liftley.sync360.features.sync.presentation.components.TransferLifecycleCard
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    uiState: SyncUiState,
    uiEffects: Flow<SyncUiEffect>,
    onEvent: (SyncEvent) -> Unit
) {
    val activeDevice = uiState.activeDevice
    var showDevicePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val visibleNearby = uiState.nearbyDevices.filter { it.id != activeDevice?.id }
    val hasDraftContent = uiState.selectedFiles.isNotEmpty() || uiState.outgoingText.isNotBlank()

    LaunchedEffect(uiEffects) {
        uiEffects.collect { effect ->
            when (effect) {
                is SyncUiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
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
                top = 16.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (activeDevice == null) {
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
            }

            item {
                QuickSaveCard(
                    enabled = uiState.quickSaveEnabled,
                    onToggle = { onEvent(SyncEvent.ToggleQuickSave) }
                )
            }

            if (activeDevice == null) {
                item {
                    SharePanel(
                        isDesktop = false,
                        uiState = uiState,
                        activeDevice = null,
                        onEvent = onEvent
                    )
                }
                item {
                    Text(
                        text = "Ready to receive",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    ReadyTransferHomeCard(
                        isScanning = uiState.isScanningForDevices,
                        nearbyDevices = visibleNearby,
                        targetActionLabel = if (hasDraftContent) "Send" else "Available",
                        onDeviceClick = {
                            onEvent(SyncEvent.SendDraftTo(it))
                        },
                        onScan = { onEvent(SyncEvent.TriggerScan) },
                        onOpenDevices = {
                            if (!uiState.isScanningForDevices) onEvent(SyncEvent.TriggerScan)
                            showDevicePicker = true
                        }
                    )
                }
                item {
                    RuntimeSecurityBanner(
                        runtime = uiState.runtimeState,
                        securityMode = uiState.securityMode
                    )
                }

                item {
                    RestartSharingCard(
                        runtime = uiState.runtimeState,
                        onRestartSharing = { onEvent(SyncEvent.RestartSharing) }
                    )
                }
            } else {
                item {
                    Text(
                        text = "Sharing with you",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (
                    uiState.fileTransferProgress != null ||
                    uiState.fileTransferFailure != null ||
                    uiState.receivedFileBatch != null
                ) {
                    item {
                        TransferLifecycleCard(
                            progress = uiState.fileTransferProgress,
                            receivedBatch = uiState.receivedFileBatch,
                            failure = uiState.fileTransferFailure,
                            onEvent = onEvent
                        )
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
                        textsList = uiState.latestTexts,
                        onCopyClick = { _ ->
                            onEvent(SyncEvent.CopyClipboard(activeDevice.id))
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
            onPairNearby = {
                onEvent(SyncEvent.SendDraftTo(it))
                showDevicePicker = false
            },
            onManualConnect = {
                onEvent(SyncEvent.RequestConnectByHost(it))
            },
            onScan = { onEvent(SyncEvent.TriggerScan) }
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
            DeviceSelectorPill(
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
private fun DeviceSelectorPill(
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
        else -> "Sync360"
    }
    Sync360Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        cornerRadius = 100.dp,
        color = colorScheme.surface,
        fillMaxWidth = false
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
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
    Sync360Surface {
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

private fun deviceIcon(type: DeviceType): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}
