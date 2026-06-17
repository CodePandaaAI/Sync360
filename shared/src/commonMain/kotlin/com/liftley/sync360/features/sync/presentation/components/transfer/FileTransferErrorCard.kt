package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.FileTransferFailure
import com.liftley.sync360.features.sync.domain.model.TransferFailureReason
import com.liftley.sync360.features.sync.presentation.SyncEvent

@Composable
fun FileTransferErrorCard(
    failure: FileTransferFailure,
    onEvent: (SyncEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val message = when (failure.reason) {
        TransferFailureReason.RECEIVER_UNAVAILABLE -> "Device is offline."
        TransferFailureReason.RECEIVER_BUSY -> "Device is busy receiving another transfer."
        TransferFailureReason.TIMED_OUT -> "Couldn't reach the device. Check that both devices are on the same Wi-Fi."
        TransferFailureReason.NETWORK_FAILED -> "Transfer stopped responding or the device refused the request."
        TransferFailureReason.WRITE_FAILED -> "Couldn't save file data."
        TransferFailureReason.INTEGRITY_FAILED -> "File verification failed or size mismatch. Please try again."
        TransferFailureReason.STORAGE_FULL -> "Not enough storage on the receiving device."
        TransferFailureReason.SENDER_CANCELLED -> "The sender cancelled the transfer."
        TransferFailureReason.RECEIVER_CANCELLED -> "Receiver declined the transfer."
        TransferFailureReason.INTERRUPTED -> "The transfer was interrupted."
        else -> failure.message.takeIf { it.isNotBlank() } ?: "Something went wrong during transfer."
    }
    val title = when (failure.reason) {
        TransferFailureReason.SENDER_CANCELLED,
        TransferFailureReason.RECEIVER_CANCELLED -> "Transfer cancelled"
        TransferFailureReason.INTERRUPTED -> "Transfer interrupted"
        else -> "Transfer failed"
    }

    Sync360Surface(modifier = modifier, cornerRadius = 24.dp) {
        Column(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
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
                textAlign = TextAlign.Center
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
