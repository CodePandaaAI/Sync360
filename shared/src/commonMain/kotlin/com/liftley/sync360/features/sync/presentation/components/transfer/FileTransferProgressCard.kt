package com.liftley.sync360.features.sync.presentation.components.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.FileTransferProgress
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferFailureReason
import com.liftley.sync360.features.sync.domain.model.TransferFilePreview
import com.liftley.sync360.features.sync.domain.model.TransferStage

@Composable
fun FileTransferProgressCard(
    progress: FileTransferProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = progress.percent / 100f)

    _root_ide_package_.com.liftley.sync360.features.sync.presentation.components.Sync360Surface(
        modifier = modifier,
        cornerRadius = 24.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val directionText =
                if (progress.direction == TransferDirection.RECEIVING) "Receiving from" else "Sending to"
            Text(
                text = "$directionText ${progress.peerName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            val stateText = when (progress.stage) {
                TransferStage.PREPARING -> "Preparing..."
                TransferStage.TRANSFERRING -> if (progress.direction == TransferDirection.RECEIVING) "Receiving..." else "Sending..."
                TransferStage.VERIFYING -> "Verifying file..."
            }
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            val filesStr =
                if (progress.files.size == 1) progress.files.first().name else "${progress.files.size} files"
            _root_ide_package_.com.liftley.sync360.features.sync.presentation.components.TransferPreviewSummaryRow(
                title = filesStr,
                subtitle = _root_ide_package_.com.liftley.sync360.features.sync.presentation.components.formatBytes(
                    progress.totalBytes
                ),
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
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )

                if (progress.speedBytesPerSecond != null && progress.estimatedTimeRemainingSeconds != null) {
                    val speedStr = formatSpeed(progress.speedBytesPerSecond)
                    val etaStr = formatEta(progress.estimatedTimeRemainingSeconds)
                    Text(
                        text = "$speedStr - $etaStr",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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

internal fun formatSpeed(bytesPerSecond: Long): String {
    val kb = bytesPerSecond / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "${mb.toString().take(4)} MB/s"
        kb >= 1.0 -> "${kb.toInt()} KB/s"
        else -> "$bytesPerSecond B/s"
    }
}

internal fun formatEta(seconds: Long): String {
    if (seconds < 60) return "$seconds sec left"
    val mins = seconds / 60
    if (mins < 60) return "$mins min left"
    val hours = mins / 60
    return "$hours hr left"
}

internal fun FileTransferProgress.fileSummaryTitle(): String {
    if (files.size == 1) return files.first().name
    return "${files.size} files"
}

internal fun FileTransferProgress.currentFile(): TransferFilePreview {
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

internal fun FileTransferProgress.progressMetaText(): String {
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

internal fun verificationCopy(fileCount: Int): String {
    return if (fileCount <= 1) {
        "Checking that everything arrived correctly"
    } else {
        "Verifying $fileCount files..."
    }
}

internal fun FileTransferFailure.uiMessage(): String = when (reason) {
    TransferFailureReason.RECEIVER_UNAVAILABLE ->
        "Couldn't reach $peerName. Check that both devices are on the same Wi-Fi and Sync360 is open."
    TransferFailureReason.RECEIVER_BUSY -> "$peerName is busy receiving another transfer."
    TransferFailureReason.TIMED_OUT ->
        "Couldn't reach $peerName. Check that both devices are on the same Wi-Fi."
    TransferFailureReason.NETWORK_FAILED,
    TransferFailureReason.INTERRUPTED -> "The transfer was interrupted."
    TransferFailureReason.WRITE_FAILED -> "Couldn't save file data."
    TransferFailureReason.INTEGRITY_FAILED -> "File verification failed. Please send it again."
    TransferFailureReason.STORAGE_FULL -> "Not enough storage on this device."
    TransferFailureReason.STORAGE_UNAVAILABLE -> "This device cannot access storage."
    TransferFailureReason.SENDER_CANCELLED -> "The sender cancelled the transfer."
    TransferFailureReason.RECEIVER_CANCELLED -> "Receiver declined the transfer."
    TransferFailureReason.SOURCE_UNAVAILABLE -> "The selected file could not be read."
    TransferFailureReason.INVALID_SELECTION -> "The selected files are invalid."
    TransferFailureReason.UNKNOWN -> message.takeIf { it.isNotBlank() } ?: "Something went wrong during transfer."
}
