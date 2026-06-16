package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFailureReason
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import com.liftley.sync360.features.sync.domain.model.TransferStage
import com.liftley.sync360.features.sync.presentation.SyncEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi

private const val SAVE_LOCATION = "Downloads / Sync360"

@Composable
fun TransferLifecycleCard(
    progress: FileTransferProgress?,
    receivedBatch: ReceivedFileBatch?,
    failure: FileTransferFailure?,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        progress != null -> ActiveTransferLifecycleCard(
            progress = progress,
            onCancel = { onEvent(SyncEvent.CancelTransfer) },
            modifier = modifier
        )
        failure != null -> FailureTransferLifecycleCard(
            failure = failure,
            onDismiss = { onEvent(SyncEvent.DismissTransferFailure) },
            modifier = modifier
        )
        receivedBatch != null -> ReceivedReceiptLifecycleCard(
            batch = receivedBatch,
            onEvent = onEvent,
            modifier = modifier
        )
    }
}

@Composable
fun ReadyTransferHomeCard(
    isScanning: Boolean,
    nearbyDevices: List<DeviceProfile>,
    onDeviceClick: (String) -> Unit,
    onScan: () -> Unit,
    onOpenDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasNearbyDevices = nearbyDevices.isNotEmpty()
    Sync360Surface(
        modifier = modifier,
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = colorScheme.primary)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = when {
                            hasNearbyDevices -> "${nearbyDevices.size} nearby device${if (nearbyDevices.size == 1) "" else "s"}"
                            isScanning -> "Searching nearby"
                            else -> "Connect a device"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (hasNearbyDevices) {
                            "Tap a device to start sharing."
                        } else {
                            "Open Sync360 on the same Wi-Fi."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Files save to $SAVE_LOCATION",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Sync360IconButton(
                    imageVector = if (isScanning) Icons.Default.Inbox else Icons.Default.Refresh,
                    contentDescription = if (isScanning) "Scanning" else "Scan again",
                    onClick = onScan
                )
            }

            if (hasNearbyDevices) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    nearbyDevices.forEach { device ->
                        ReadyDeviceRow(
                            device = device,
                            onClick = { onDeviceClick(device.id) }
                        )
                    }
                }
            } else {
                Sync360Surface(
                    modifier = Modifier.clickable(onClick = onOpenDevices),
                    cornerRadius = 20.dp,
                    color = colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Wifi else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isScanning) "Searching local network..." else "Scan for devices",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyDeviceRow(
    device: DeviceProfile,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface(
        modifier = Modifier.clickable(onClick = onClick),
        cornerRadius = 20.dp,
        color = colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = readyDeviceIcon(device.type),
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.hostAddress ?: "Available",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Connect",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
        }
    }
}

private fun readyDeviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}

@Composable
private fun ActiveTransferLifecycleCard(
    progress: FileTransferProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.percent / 100f
    )
    val directionLabel = if (progress.direction == TransferDirection.RECEIVING) {
        "Receiving from"
    } else {
        "Sending to"
    }
    val actionLine = when (progress.stage) {
        TransferStage.PREPARING -> "Connecting to ${progress.peerName}..."
        TransferStage.TRANSFERRING -> if (progress.direction == TransferDirection.RECEIVING) {
            "${progress.peerName} is sending..."
        } else {
            "${progress.peerName} is receiving..."
        }
        TransferStage.VERIFYING -> verificationCopy(progress.files.size)
    }
    val currentFile = progress.currentFile()

    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TransferStatusGlyph(
                    progress = progress.percent,
                    icon = if (progress.direction == TransferDirection.SENDING) {
                        Icons.AutoMirrored.Filled.Send
                    } else {
                        Icons.Default.Inbox
                    }
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "$directionLabel ${progress.peerName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = actionLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            TransferPreviewSummaryRow(
                title = progress.fileSummaryTitle(),
                subtitle = "${formatBytes(progress.totalBytes)} total",
                files = progress.files
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${progress.percent}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Text(
                    text = progress.progressMetaText(),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (progress.direction == TransferDirection.RECEIVING) {
                        "Currently receiving ${currentFile.name}"
                    } else {
                        "Currently sending ${currentFile.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface
                )
                if (progress.direction == TransferDirection.RECEIVING) {
                    Text(
                        text = "Saving to $SAVE_LOCATION",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceivedReceiptLifecycleCard(
    batch: ReceivedFileBatch,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isSingleFile = batch.savedPaths.size == 1
    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SuccessGlyph()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Received from ${batch.senderName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (isSingleFile) {
                            "${batch.files.firstOrNull()?.name.orEmpty()} saved to $SAVE_LOCATION"
                        } else {
                            "${batch.files.size} files saved to $SAVE_LOCATION"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onEvent(SyncEvent.DismissReceivedFiles) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            TransferPreviewSummaryRow(
                title = if (isSingleFile) {
                    batch.files.firstOrNull()?.name ?: "File saved"
                } else {
                    "${batch.files.size} files saved"
                },
                subtitle = formatBytes(batch.files.sumOf { it.sizeBytes }),
                files = batch.files
            )

            ReceivedFilesCarousel(
                files = batch.files,
                savedPaths = batch.savedPaths,
                onOpenFile = { path -> onEvent(SyncEvent.OpenFile(path)) }
            )

            if (isSingleFile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onEvent(SyncEvent.OpenFile(batch.savedPaths.first())) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Open file", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onEvent(SyncEvent.ShowFileInFolder(batch.savedPaths.first())) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Show folder", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.OpenDownloadsFolder) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Show in folder", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReceivedFilesCarousel(
    files: List<TransferFilePreview>,
    savedPaths: List<String>,
    onOpenFile: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(files) { index, file ->
            val path = savedPaths.getOrNull(index)
            ReceivedFileTile(
                file = file,
                path = path,
                onOpenFile = onOpenFile
            )
        }
    }
}

@Composable
private fun ReceivedFileTile(
    file: TransferFilePreview,
    path: String?,
    onOpenFile: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val tileModifier = if (path != null) {
        Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpenFile(path) }
    } else {
        Modifier.width(132.dp)
    }

    Surface(
        modifier = tileModifier,
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colorScheme.primaryContainer
            ) {
                Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = file.receiptIcon(),
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBytes(file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun TransferFilePreview.receiptIcon(): ImageVector {
    return if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
        Icons.Default.PermMedia
    } else {
        Icons.Default.Description
    }
}

@Composable
private fun FailureTransferLifecycleCard(
    failure: FileTransferFailure,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isCancelled = failure.reason == TransferFailureReason.SENDER_CANCELLED ||
        failure.reason == TransferFailureReason.RECEIVER_CANCELLED
    val title = when {
        isCancelled -> "Transfer cancelled"
        failure.reason == TransferFailureReason.INTERRUPTED -> "Transfer interrupted"
        else -> "Transfer failed"
    }
    val message = failure.uiMessage()

    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = colorScheme.errorContainer,
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = "$message\nPartial files were removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TransferStatusGlyph(
    progress: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(58.dp),
            strokeWidth = 4.dp,
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHighest
        )
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = colorScheme.primaryContainer) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SuccessGlyph() {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = colorScheme.primaryContainer,
        modifier = Modifier.size(58.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
fun FileTransferProgressCard(
    progress: FileTransferProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = progress.percent / 100f)
    
    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val directionText = if (progress.direction == TransferDirection.RECEIVING) "Receiving from" else "Sending to"
            Text(
                text = "$directionText ${progress.peerName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            
            val stateText = when (progress.stage) {
                TransferStage.PREPARING -> "Connecting..."
                TransferStage.TRANSFERRING -> if (progress.direction == TransferDirection.RECEIVING) "Receiving..." else "Sending..."
                TransferStage.VERIFYING -> "Verifying file..."
            }
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            
            val filesStr = if (progress.files.size == 1) progress.files.first().name else "${progress.files.size} files"
            TransferPreviewSummaryRow(
                title = filesStr,
                subtitle = formatBytes(progress.totalBytes),
                files = progress.files
            )
            
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
            
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "${progress.percent}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                
                if (progress.speedBytesPerSecond != null && progress.estimatedTimeRemainingSeconds != null) {
                    val speedStr = formatSpeed(progress.speedBytesPerSecond)
                    val etaStr = formatEta(progress.estimatedTimeRemainingSeconds)
                    Text(
                        text = "$speedStr · $etaStr",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.error)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    val kb = bytesPerSecond / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "${mb.toString().take(4)} MB/s"
        kb >= 1.0 -> "${kb.toInt()} KB/s"
        else -> "$bytesPerSecond B/s"
    }
}

private fun formatEta(seconds: Long): String {
    if (seconds < 60) return "$seconds sec left"
    val mins = seconds / 60
    if (mins < 60) return "$mins min left"
    val hours = mins / 60
    return "$hours hr left"
}

private fun FileTransferProgress.fileSummaryTitle(): String {
    if (files.size == 1) return files.first().name
    return "${files.size} files"
}

private fun FileTransferProgress.currentFile(): TransferFilePreview {
    if (files.isEmpty()) {
        return TransferFilePreview("file", "application/octet-stream", totalBytes)
    }
    var seen = 0L
    files.forEach { file ->
        val next = seen + file.sizeBytes
        if (bytesTransferred < next) return file
        seen = next
    }
    return files.last()
}

private fun FileTransferProgress.progressMetaText(): String {
    if (stage == TransferStage.VERIFYING) return "Verifying..."
    if (percent >= 98 && bytesTransferred < totalBytes) return "Almost done..."
    val speed = speedBytesPerSecond?.takeIf { it > 0L } ?: return "Calculating..."
    val eta = estimatedTimeRemainingSeconds?.takeIf { it > 0L }
    return if (eta == null) {
        formatSpeed(speed)
    } else {
        "${formatSpeed(speed)} - ${formatEta(eta)}"
    }
}

private fun verificationCopy(fileCount: Int): String {
    return if (fileCount <= 1) {
        "Checking that everything arrived correctly"
    } else {
        "Verifying $fileCount files..."
    }
}

private fun FileTransferFailure.uiMessage(): String = when (reason) {
    TransferFailureReason.RECEIVER_UNAVAILABLE ->
        "Couldn't reach $peerName. Check that both devices are on the same Wi-Fi and Sync360 is open."
    TransferFailureReason.TIMED_OUT ->
        "Couldn't reach $peerName. Check that both devices are on the same Wi-Fi."
    TransferFailureReason.NETWORK_FAILED,
    TransferFailureReason.INTERRUPTED -> "The transfer was interrupted."
    TransferFailureReason.WRITE_FAILED -> "Couldn't save file data."
    TransferFailureReason.INTEGRITY_FAILED -> "File verification failed. Please send it again."
    TransferFailureReason.STORAGE_FULL -> "Not enough storage on this device."
    TransferFailureReason.STORAGE_UNAVAILABLE -> "This device cannot access storage."
    TransferFailureReason.SENDER_CANCELLED -> "The sender cancelled the transfer."
    TransferFailureReason.RECEIVER_CANCELLED -> "Receiver cancelled the transfer."
    TransferFailureReason.SOURCE_UNAVAILABLE -> "The selected file could not be read."
    TransferFailureReason.INVALID_SELECTION -> "The selected files are invalid."
    TransferFailureReason.UNKNOWN -> message.takeIf { it.isNotBlank() } ?: "Something went wrong during transfer."
}

@Composable
fun ReceivedFileBatchCard(
    batch: ReceivedFileBatch,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = batch.senderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                androidx.compose.material3.IconButton(
                    onClick = { onEvent(SyncEvent.DismissReceivedFiles) },
                    modifier = Modifier.size(24.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "Saved to Downloads",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            TransferPreviewSummaryRow(
                title = "${batch.files.size} file${if (batch.files.size == 1) "" else "s"} saved",
                subtitle = formatBytes(batch.files.sumOf { it.sizeBytes }),
                files = batch.files
            )
            Button(
                onClick = {
                    if (batch.savedPaths.size == 1) {
                        onEvent(SyncEvent.OpenFile(batch.savedPaths.first()))
                    } else {
                        onEvent(SyncEvent.OpenDownloadsFolder)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(if (batch.files.size == 1) "Open File" else "Show in folder", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FileTransferErrorCard(
    failure: com.liftley.sync360.features.sync.domain.model.FileTransferFailure,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val message = when (failure.reason) {
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.RECEIVER_UNAVAILABLE -> "Device is busy or offline."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.TIMED_OUT -> "Couldn’t reach the device. Check that both devices are on the same Wi-Fi."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.NETWORK_FAILED -> "Transfer stopped responding or connection refused."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.WRITE_FAILED -> "Couldn’t save file data."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.INTEGRITY_FAILED -> "File verification failed or size mismatch. Please try again."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.STORAGE_FULL -> "Not enough storage on the receiving device."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.SENDER_CANCELLED -> "The sender cancelled the transfer."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.RECEIVER_CANCELLED -> "Receiver cancelled the transfer."
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.INTERRUPTED -> "The transfer was interrupted."
        else -> failure.message.takeIf { it.isNotBlank() } ?: "Something went wrong during transfer."
    }
    val title = when (failure.reason) {
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.SENDER_CANCELLED,
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.RECEIVER_CANCELLED -> "Transfer cancelled"
        com.liftley.sync360.features.sync.domain.model.TransferFailureReason.INTERRUPTED -> "Transfer interrupted"
        else -> "Transfer failed"
    }

    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                modifier = Modifier.size(48.dp),
                tint = colorScheme.error
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.material3.TextButton(
                onClick = { onEvent(SyncEvent.DismissTransferFailure) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss", fontWeight = FontWeight.Bold, color = colorScheme.onSurfaceVariant)
            }
        }
    }
}
