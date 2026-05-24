package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.presentation.components.ClipboardHistorySection
import com.liftley.sync360.features.sync.presentation.components.ConfirmDialogs
import com.liftley.sync360.features.sync.presentation.components.MobileDevicePickerSheet
import com.liftley.sync360.features.sync.presentation.components.ReadyToSyncCard
import com.liftley.sync360.features.sync.presentation.components.SharePanel
import com.liftley.sync360.features.sync.presentation.components.TransferredFilesSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    val activeDevice = uiState.connectedDevices.firstOrNull { it.id == uiState.activeDeviceId }
        ?: uiState.nearbyDevices.firstOrNull { it.id == uiState.activeDeviceId }
    val activeStream = uiState.activeDeviceId?.let { uiState.deviceStreams[it] }
    var showDevicePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var copiedFeedbackTrigger by remember { mutableStateOf(0) }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SyncTopBar(
                connectionStatus = uiState.connectionStatus,
                activeDevice = activeDevice,
                onOpenDevicePicker = { showDevicePicker = true }
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
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

                item {
                    ClipboardHistorySection(
                        textsList = activeStream?.latestTexts ?: emptyList(),
                        onCopyClick = { _ ->
                            onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                            copiedFeedbackTrigger++
                        }
                    )
                }

                item {
                    val mediaList = activeStream?.media.orEmpty()
                    val docList = activeStream?.documents.orEmpty()
                    val combinedFiles = (mediaList + docList).sortedByDescending { it.id }

                    TransferredFilesSection(
                        combinedFiles = combinedFiles,
                        title = "Shared Media & Files"
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
            }
        )
    }

    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncTopBar(
    connectionStatus: ConnectionStatus,
    activeDevice: DeviceProfile?,
    onOpenDevicePicker: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    TopAppBar(
        title = {
            Text(
                text = "Sync360",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isConnected) {
                        colorScheme.primaryContainer
                    } else {
                        colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Connection status",
                            tint = if (isConnected) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.size(Spacing.md)
                        )
                        Text(
                            text = if (isConnected) "Connected" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isConnected) {
                                colorScheme.onPrimaryContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenDevicePicker),
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = "Select device",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Spacing.md)
                        )
                        Text(
                            text = activeDevice?.name ?: "Choose device",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Open device list",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Spacing.lg)
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.background,
            titleContentColor = colorScheme.onBackground
        )
    )
}
