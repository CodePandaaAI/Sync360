package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.features.sync.presentation.SyncUiState

@Composable
fun ConfirmDialogs(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    if (uiState.send.pendingOutgoingOfferTarget != null) {
        val target = uiState.send.pendingOutgoingOfferTarget
        val itemCount = uiState.send.selectedItems.size

        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.CancelSendProposal) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Send to ${target.name}?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val contentText = "$itemCount item${if (itemCount == 1) "" else "s"}"
                Text(
                    text = "We want to send these $contentText to this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.SendSelectedItemsTo(target.id)) },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Send", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SyncEvent.CancelSendProposal) }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
