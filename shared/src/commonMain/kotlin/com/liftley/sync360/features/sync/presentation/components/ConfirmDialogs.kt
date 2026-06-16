package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.features.sync.presentation.SyncUiState
import com.liftley.sync360.features.sync.domain.model.ConnectionState
import com.liftley.sync360.features.sync.domain.model.PendingIncomingOffer

@Composable
fun ConfirmDialogs(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    if (uiState.pendingIncomingOffer != null) {
        val offer = uiState.pendingIncomingOffer
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DeclineIncomingOffer(offer.offerId)) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Receive from ${offer.senderName}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = offer.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.AcceptIncomingOffer(offer.offerId)) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SyncEvent.DeclineIncomingOffer(offer.offerId)) }) {
                    Text("Decline", fontWeight = FontWeight.Bold)
                }
            }
        )
    } else uiState.pendingIncomingRequest?.let { device ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DeclineConnection(device.id)) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Device request",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${device.name} wants to become available on your local network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.AcceptConnection(device.id)) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SyncEvent.DeclineConnection(device.id)) }) {
                    Text("Decline", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Outgoing peer-grant confirmation kept for compatibility.
    uiState.pendingOutgoingRequest
        ?.takeIf { uiState.connectionState is ConnectionState.ResolvingRoute }
        ?.let { device ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DismissConnectRequest) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Make device available?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Allow ${device.name} as a nearby send target for this Sync360 run?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.ConfirmConnect) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SyncEvent.DismissConnectRequest) }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private fun PendingIncomingOffer.description(): String = when (this) {
    is PendingIncomingOffer.Files ->
        "$fileCount file${if (fileCount == 1) "" else "s"} - ${totalBytes.formatBytes()}"
    is PendingIncomingOffer.Text ->
        "Text: ${preview.ifBlank { "(empty)" }}"
}

private fun Long.formatBytes(): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) {
        "${toLong()} ${units[index]}"
    } else {
        "${(value * 10).toInt() / 10.0} ${units[index]}"
    }
}
