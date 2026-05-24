package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liftley.sync360.features.sync.presentation.SyncUiState
import com.liftley.sync360.features.sync.presentation.SyncEvent

@Composable
fun ConfirmDialogs(
    uiState: SyncUiState,
    onEvent: (SyncEvent) -> Unit
) {
    // Explicit Connect Request Dialog
    uiState.pendingConnectDevice?.let { device ->
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
                    text = "Do you want to pair and connect with ${device.name} over your local Wi-Fi network?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.ConfirmConnect) },
                    shape = RoundedCornerShape(12.dp)
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

    // Elegant Receive File Confirmation Dialog
    uiState.pendingFileOffer?.let { offer ->
        AlertDialog(
            onDismissRequest = { onEvent(SyncEvent.DeclineFileOffer) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Incoming File Offer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${offer.senderName} wants to send you a file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (offer.mimeType.startsWith("image/") || offer.mimeType.startsWith("video/")) 
                                    Icons.Default.Share 
                                else Icons.Default.Folder,
                                contentDescription = "Incoming File Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = offer.fileName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${offer.fileSize / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(SyncEvent.AcceptFileOffer) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Receive & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(SyncEvent.DeclineFileOffer) }) {
                    Text("Decline", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
