package com.liftley.sync360.features.sync.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.presentation.components.transfer.FileTransferErrorCard
import com.liftley.sync360.features.sync.presentation.components.transfer.FileTransferProgressCard
import com.liftley.sync360.features.sync.presentation.components.Sync360Surface
import com.liftley.sync360.features.sync.presentation.components.SyncBottomNavigationBar
import com.liftley.sync360.features.sync.presentation.components.formatBytes
import com.liftley.sync360.features.sync.presentation.navigation.SyncRoute
import com.liftley.sync360.features.sync.presentation.receive.ItemTransferStatus
import com.liftley.sync360.features.sync.presentation.receive.ProgressFileRow
import com.liftley.sync360.features.sync.presentation.receive.ReceiveProposalCard
import com.liftley.sync360.features.sync.presentation.receive.ReceivedFileRow
import com.liftley.sync360.features.sync.presentation.receive.getFileStatuses
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    uiState: SyncUiState,
    uiEffects: Flow<SyncUiEffect>,
    onEvent: (SyncEvent) -> Unit,
    showBackButton: Boolean = true,
    onBack: () -> Unit = {},
    currentRoute: SyncRoute = SyncRoute.Receive,
    showBottomBar: Boolean = false,
    showSettingsAction: Boolean = false,
    onNavigateRoute: (SyncRoute) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiEffects) {
        uiEffects.collect { effect ->
            when (effect) {
                is SyncUiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val received = uiState.receive.receivedFileBatch
    val isSingleFile = received?.savedPaths?.size == 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (received != null) "Transfer Summary" else "Receive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (showBackButton && received == null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (showSettingsAction && received == null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
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
        if (received != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Received from ${received.senderName}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        val totalBytes = received.files.sumOf { it.sizeBytes }
                        val fileCountText = if (isSingleFile) "1 file" else "${received.files.size} files"
                        Text(
                            text = "$fileCountText (${formatBytes(totalBytes)}) saved to Downloads / Sync360",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "Files Transferred",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Sync360Surface(cornerRadius = 16.dp) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            received.files.forEachIndexed { index, file ->
                                val path = received.savedPaths.getOrNull(index)
                                ReceivedFileRow(
                                    file = file,
                                    path = path,
                                    onOpenFile = { onEvent(SyncEvent.OpenFile(it)) }
                                )
                                if (index < received.files.lastIndex) {
                                    HorizontalDivider(
                                        color = colorScheme.outlineVariant,
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSingleFile) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onEvent(SyncEvent.OpenFile(received.savedPaths.first())) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text("Open File", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onEvent(SyncEvent.ShowFileInFolder(received.savedPaths.first())) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Text("Show Folder", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onEvent(SyncEvent.OpenDownloadsFolder) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text("Show in Folder", fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { onEvent(SyncEvent.DismissReceivedFiles) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val offer = uiState.receive.pendingIncomingOffer
                val progress = uiState.receive.fileTransferProgress
                val failure = uiState.receive.fileTransferFailure

                when {
                    offer != null -> {
                        item {
                            ReceiveProposalCard(
                                offer = offer,
                                onAccept = { onEvent(SyncEvent.AcceptIncomingOffer(offer.offerId)) },
                                onDecline = { onEvent(SyncEvent.DeclineIncomingOffer(offer.offerId)) }
                            )
                        }
                    }
                    progress != null && progress.direction == TransferDirection.RECEIVING -> {
                        item {
                            FileTransferProgressCard(
                                progress = progress,
                                onCancel = { onEvent(SyncEvent.CancelTransfer) }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = "Receiving Files",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            val statuses = getFileStatuses(progress.files, progress.bytesTransferred)
                            Sync360Surface(cornerRadius = 16.dp) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    progress.files.forEachIndexed { index, file ->
                                        val status = statuses.getOrNull(index) ?: ItemTransferStatus.QUEUED
                                        ProgressFileRow(
                                            file = file,
                                            status = status
                                        )
                                        if (index < progress.files.lastIndex) {
                                            HorizontalDivider(
                                                color = colorScheme.outlineVariant,
                                                thickness = 1.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    failure != null && failure.direction == TransferDirection.RECEIVING -> {
                        item {
                            FileTransferErrorCard(
                                failure = failure,
                                onEvent = onEvent
                            )
                        }
                    }
                    else -> {
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
                                        color = colorScheme.primaryContainer,
                                        modifier = Modifier.size(80.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Inbox,
                                                contentDescription = null,
                                                tint = colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Ready to receive",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Waiting for nearby devices to share files or text. Keep Sync360 open on the same local network.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
