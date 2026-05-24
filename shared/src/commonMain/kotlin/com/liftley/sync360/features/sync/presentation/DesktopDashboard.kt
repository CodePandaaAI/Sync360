package com.liftley.sync360.features.sync.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.presentation.components.*
import kotlinx.coroutines.delay

@Composable
fun DesktopDashboard(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeDevice = uiState.connectedDevices.firstOrNull { it.id == uiState.activeDeviceId }
        ?: uiState.nearbyDevices.firstOrNull { it.id == uiState.activeDeviceId }
    val activeStream = uiState.activeDeviceId?.let { uiState.deviceStreams[it] }
    var copiedFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedFeedbackText) {
        if (copiedFeedbackText != null) {
            delay(1500)
            copiedFeedbackText = null
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            copiedFeedbackText = msg
            onEvent(SyncEvent.ClearUserMessage)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // Desktop Left Sidebar Rail
        DesktopDeviceRail(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
        )

        // Desktop Main Content Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (activeDevice == null) {
                    ReadyToSyncCard(
                        isDesktop = true
                    )
                } else {
                    // Header Status Area
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Connected with ${activeDevice.name}",
                            fontSize = 28.sp,
                            style = MaterialTheme.typography.headlineLarge,
                            color = colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) 
                                "Direct active local network pairing" 
                            else "Standby - waiting for connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    // Direct Share Center (Always Active)
                    SharePanel(
                        isDesktop = true,
                        uiState = uiState,
                        activeDevice = activeDevice,
                        onEvent = onEvent
                    )

                    // Structured History (Text + Files side by side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 5 Latest Clipboard Texts
                        ClipboardHistorySection(
                            textsList = activeStream?.latestTexts ?: emptyList(),
                            onCopyClick = { clipboard ->
                                onEvent(SyncEvent.CopyClipboard(activeDevice.id))
                                copiedFeedbackText = clipboard.text
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Shared Media & Documents
                        val mediaList = activeStream?.media.orEmpty()
                        val docList = activeStream?.documents.orEmpty()
                        val combinedFiles = (mediaList + docList).sortedByDescending { it.id }

                        TransferredFilesSection(
                            combinedFiles = combinedFiles,
                            title = "Transferred Files",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // HUD Feedback
            androidx.compose.animation.AnimatedVisibility(
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

    // Connect & File Offer Dialogs
    ConfirmDialogs(uiState = uiState, onEvent = onEvent)
}
