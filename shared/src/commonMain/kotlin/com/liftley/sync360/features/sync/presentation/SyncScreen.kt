package com.liftley.sync360.features.sync.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.presentation.components.*
import kotlinx.coroutines.delay

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
    var copiedFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedFeedbackText) {
        if (copiedFeedbackText != null) {
            delay(1500)
            copiedFeedbackText = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                // OneUI-style spacious compact header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Sync360",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-1).sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Status Pill
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.clip(CircleShape)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = "Wifi State",
                                        tint = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                                            MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) "Connected" else "Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Device Selector Pill
                            Surface(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showDevicePicker = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Devices,
                                        contentDescription = "Active Device",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = activeDevice?.name ?: "Choose device",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Device",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
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
                    // Direct Share Center (Always Active)
                    item {
                        SharePanel(
                            isDesktop = false,
                            uiState = uiState,
                            activeDevice = activeDevice,
                            onEvent = onEvent
                        )
                    }

                    // 5 Latest Texts History List
                    item {
                        ClipboardHistorySection(
                            textsList = activeStream?.latestTexts ?: emptyList(),
                            onCopyClick = { clipboard ->
                                onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                                copiedFeedbackText = clipboard.text
                            }
                        )
                    }

                    // Recent Shared Media & Documents
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

            // HUD HUD Feedback
            AnimatedVisibility(
                visible = copiedFeedbackText != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 6.dp
                ) {
                    Text(
                        text = "Copied to clipboard",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold
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

    // Connect & File Offer Dialogs
    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}
