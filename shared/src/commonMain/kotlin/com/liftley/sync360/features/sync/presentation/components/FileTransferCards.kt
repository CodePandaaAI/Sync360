package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.ReceivedFileBatch
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferStage
import com.liftley.sync360.features.sync.presentation.SyncEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline

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
