package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
            Text(
                text = batch.senderName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = "Received",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            TransferPreviewSummaryRow(
                title = "${batch.files.size} file${if (batch.files.size == 1) "" else "s"} saved",
                subtitle = formatBytes(batch.files.sumOf { it.sizeBytes }),
                files = batch.files
            )
            Button(
                onClick = { onEvent(SyncEvent.DismissReceivedFiles) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    }
}
