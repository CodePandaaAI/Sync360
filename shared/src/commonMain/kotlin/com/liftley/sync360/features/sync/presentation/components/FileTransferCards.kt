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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = progress.peerName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = when (progress.stage) {
                    TransferStage.PREPARING -> "Preparing files"
                    TransferStage.TRANSFERRING ->
                        if (progress.direction == TransferDirection.RECEIVING) "Receiving files" else "Sending files"
                    TransferStage.VERIFYING -> "Verifying files"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            TransferPreviewSummaryRow(
                title = "${progress.files.size} file${if (progress.files.size == 1) "" else "s"}",
                subtitle = formatBytes(progress.files.sumOf { it.sizeBytes }),
                files = progress.files
            )
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.primary,
                trackColor = colorScheme.surfaceContainer
            )
            Text(
                text = "${progress.percent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
        }
    }
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
        else -> failure.message.takeIf { it.isNotBlank() } ?: "Something went wrong during transfer."
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
                text = "Transfer failed",
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
