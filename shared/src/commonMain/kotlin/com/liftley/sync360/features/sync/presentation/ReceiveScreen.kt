package com.liftley.sync360.features.sync.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import coil3.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import com.liftley.sync360.features.sync.presentation.components.FileTransferErrorCard
import com.liftley.sync360.features.sync.presentation.components.FileTransferProgressCard
import com.liftley.sync360.features.sync.presentation.components.Sync360Surface
import com.liftley.sync360.features.sync.presentation.components.SyncBottomNavigationBar
import com.liftley.sync360.features.sync.presentation.components.formatBytes
import com.liftley.sync360.features.sync.presentation.navigation.SyncRoute
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

    val received = uiState.receivedFileBatch
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
                val offer = uiState.pendingIncomingOffer
                val progress = uiState.fileTransferProgress
                val failure = uiState.fileTransferFailure

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

@Composable
private fun ReceiveProposalCard(
    offer: PendingIncomingOffer,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val description = when (offer) {
        is PendingIncomingOffer.Files ->
            "${offer.fileCount} file${if (offer.fileCount == 1) "" else "s"} (${formatBytes(offer.totalBytes)})"
        is PendingIncomingOffer.Text ->
            "Text snippet: \"${offer.preview}\""
    }

    Sync360Surface(cornerRadius = 28.dp) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (offer is PendingIncomingOffer.Text) Icons.AutoMirrored.Filled.Article else Icons.Default.Inbox,
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Incoming share from",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = offer.senderName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error)
                ) {
                    Text("Decline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ReceivedFileRow(
    file: TransferFilePreview,
    path: String?,
    onOpenFile: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isImage = file.mimeType.startsWith("image/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (path != null) Modifier.clickable { onOpenFile(path) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isImage && path != null) {
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) {
                                Icons.Default.PermMedia
                            } else {
                                Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBytes(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Received",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open file",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ProgressFileRow(
    file: TransferFilePreview,
    status: ItemTransferStatus
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (status) {
                    ItemTransferStatus.COMPLETED -> colorScheme.primaryContainer
                    ItemTransferStatus.TRANSFERRING -> colorScheme.secondaryContainer
                    ItemTransferStatus.QUEUED -> colorScheme.surfaceVariant
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("video/")) {
                            Icons.Default.PermMedia
                        } else {
                            Icons.Default.Description
                        },
                        contentDescription = null,
                        tint = when (status) {
                            ItemTransferStatus.COMPLETED -> colorScheme.onPrimaryContainer
                            ItemTransferStatus.TRANSFERRING -> colorScheme.onSecondaryContainer
                            ItemTransferStatus.QUEUED -> colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                        ItemTransferStatus.QUEUED -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBytes(file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = when (status) {
                    ItemTransferStatus.COMPLETED -> "Received"
                    ItemTransferStatus.TRANSFERRING -> "Transferring"
                    ItemTransferStatus.QUEUED -> "Queued"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    ItemTransferStatus.COMPLETED -> colorScheme.primary
                    ItemTransferStatus.TRANSFERRING -> colorScheme.secondary
                    ItemTransferStatus.QUEUED -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )

            when (status) {
                ItemTransferStatus.COMPLETED -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Received",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                ItemTransferStatus.TRANSFERRING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.secondary
                    )
                }
                ItemTransferStatus.QUEUED -> {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Queued",
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private enum class ItemTransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED
}

private fun getFileStatuses(
    files: List<TransferFilePreview>,
    bytesTransferred: Long
): List<ItemTransferStatus> {
    var accumulatedBytes = 0L
    return files.map { file ->
        val fileStart = accumulatedBytes
        val fileEnd = accumulatedBytes + file.sizeBytes
        accumulatedBytes = fileEnd

        when {
            bytesTransferred >= fileEnd -> ItemTransferStatus.COMPLETED
            bytesTransferred >= fileStart -> ItemTransferStatus.TRANSFERRING
            else -> ItemTransferStatus.QUEUED
        }
    }
}
