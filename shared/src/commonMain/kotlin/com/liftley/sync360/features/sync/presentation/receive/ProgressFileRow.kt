package com.liftley.sync360.features.sync.presentation.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import com.liftley.sync360.features.sync.presentation.components.formatBytes
@Composable
internal fun ProgressFileRow(
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

internal enum class ItemTransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED
}

internal fun getFileStatuses(
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
