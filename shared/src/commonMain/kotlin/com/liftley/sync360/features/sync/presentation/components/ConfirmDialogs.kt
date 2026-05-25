package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.liftley.sync360.core.designsystem.SyncDimens
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.features.sync.presentation.SyncUiState

@Composable
fun ConfirmDialogs(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    uiState.pendingPairingRequests.firstOrNull()?.let { device ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DeclinePairing(device.id)) },
            shape = RoundedCornerShape(SyncDimens.cornerMedium),
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
                    onClick = { onEvent(SyncEvent.AcceptPairing(device.id)) },
                    shape = RoundedCornerShape(SyncDimens.cornerSmall)
                ) {
                    Text("Accept", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SyncEvent.DeclinePairing(device.id)) }) {
                    Text("Decline", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Outgoing connect confirmation
    uiState.pendingConnectDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DismissConnectRequest) },
            shape = RoundedCornerShape(SyncDimens.cornerMedium),
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
                    text = "Do you want to pair and connect with ${device.name} over your local Wi-Fi network?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.ConfirmConnect) },
                    shape = RoundedCornerShape(SyncDimens.cornerSmall)
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
