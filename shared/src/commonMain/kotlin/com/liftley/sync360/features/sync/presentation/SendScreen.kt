package com.liftley.sync360.features.sync.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.presentation.components.ClipboardHistorySection
import com.liftley.sync360.features.sync.presentation.components.ConfirmDialogs
import com.liftley.sync360.features.sync.presentation.components.FileTransferErrorCard
import com.liftley.sync360.features.sync.presentation.components.FileTransferProgressCard
import com.liftley.sync360.features.sync.presentation.components.Sync360Surface
import com.liftley.sync360.features.sync.presentation.components.SyncBottomNavigationBar
import com.liftley.sync360.features.sync.presentation.navigation.SyncRoute
import com.liftley.sync360.features.sync.presentation.send.ManualIpConnectCard
import com.liftley.sync360.features.sync.presentation.send.UnifiedSelectionPanel
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    uiState: SyncUiState,
    uiEffects: Flow<SyncUiEffect>,
    onEvent: (SyncEvent) -> Unit,
    currentRoute: SyncRoute = SyncRoute.Send,
    showBottomBar: Boolean = false,
    showSettingsAction: Boolean = false,
    onNavigateRoute: (SyncRoute) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val visibleNearby = uiState.discovery.nearbyDevices
    val hasDraftContent = uiState.send.selectedItems.isNotEmpty()

    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var showManualInput by rememberSaveable { mutableStateOf(false) }

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
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Send",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "This Device: ${uiState.runtime.localDeviceName} • ${uiState.runtime.serverIp}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Quick Save",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = uiState.receive.quickSaveEnabled,
                                onCheckedChange = { onEvent(SyncEvent.ToggleQuickSave) },
                                thumbContent = {
                                    if (uiState.receive.quickSaveEnabled) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                }
                            )
                            if (showSettingsAction) {
                                IconButton(onClick = onOpenSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        }
                    }
                )

                SecondaryTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Send Items", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Recent Clipboard", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                SyncBottomNavigationBar(
                    currentRoute = currentRoute,
                    onRouteSelected = onNavigateRoute
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (activeTab == 0) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Inline Sending Progress / Failure
                val progress = uiState.receive.fileTransferProgress
                val failure = uiState.receive.fileTransferFailure
                if (progress != null && progress.direction == TransferDirection.SENDING) {
                    item {
                        FileTransferProgressCard(
                            progress = progress,
                            onCancel = { onEvent(SyncEvent.CancelTransfer) }
                        )
                    }
                } else if (failure != null && failure.direction == TransferDirection.SENDING) {
                    item {
                        FileTransferErrorCard(
                            failure = failure,
                            onEvent = onEvent
                        )
                    }
                }

                // Unified Selection Panel
                item {
                    UnifiedSelectionPanel(
                        selectedItems = uiState.send.selectedItems,
                        outgoingText = uiState.send.outgoingText,
                        onEvent = onEvent
                    )
                }

                // Devices Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Devices on the network",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (visibleNearby.isNotEmpty()) {
                            if (uiState.discovery.isScanningForDevices) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Scanning",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { onEvent(SyncEvent.TriggerScan) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Scan",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Nearby Devices List
                if (visibleNearby.isNotEmpty()) {
                    items(visibleNearby) { device ->
                        Sync360Surface(
                            modifier = Modifier.clickable {
                                onEvent(SyncEvent.ProposeSendTo(device.id))
                            },
                            cornerRadius = 16.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = when (device.type) {
                                                    DeviceType.PHONE -> Icons.Default.Smartphone
                                                    DeviceType.TABLET -> Icons.Default.Tablet
                                                    else -> Icons.Default.Laptop
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = device.hostAddress ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (hasDraftContent) "Send" else "Available",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasDraftContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Sync360Surface(
                            modifier = Modifier.clickable {
                                if (!uiState.discovery.isScanningForDevices) onEvent(SyncEvent.TriggerScan)
                            },
                            cornerRadius = 16.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (uiState.discovery.isScanningForDevices) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Searching local network...", style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Scan for devices", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Collapsible Manual Connection Section
                if (!showManualInput) {
                    item {
                        TextButton(onClick = { showManualInput = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Connect manually using IP address")
                        }
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ManualIpConnectCard(
                                onConnect = {
                                    onEvent(SyncEvent.SendSelectedItemsToHost(it))
                                    showManualInput = false
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { showManualInput = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        } else {
            // Tab 2: Recent Clipboard Logs
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.send.latestTexts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "No clipboard entries",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Texts received from nearby devices will appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    item {
                        ClipboardHistorySection(
                            textsList = uiState.send.latestTexts,
                            onCopyClick = { entry ->
                                onEvent(SyncEvent.CopyClipboard(entry.text))
                            }
                        )
                    }
                }
            }
        }
    }

    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}
