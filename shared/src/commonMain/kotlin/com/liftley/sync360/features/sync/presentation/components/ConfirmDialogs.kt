package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.features.sync.presentation.SyncUiState
import com.liftley.sync360.features.sync.domain.model.ConnectionState

@Composable
fun ConfirmDialogs(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    uiState.pendingIncomingRequest?.let { device ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DeclineConnection(device.id)) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Connection request",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${device.name} wants to connect on your local network.",
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

    // Outgoing connect confirmation
    uiState.pendingOutgoingRequest
        ?.takeIf { uiState.connectionState is ConnectionState.ResolvingRoute }
        ?.let { device ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DismissConnectRequest) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Connect to device?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Do you want to connect with ${device.name} for this Sync360 session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.ConfirmConnect) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
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
