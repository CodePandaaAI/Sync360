package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState

@Composable
fun FileOfferStateUi(
    state: ReceiveScreenState.IncomingFileOffer,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = state.senderDeviceName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Text(
                    text = "Wants to send ${state.fileCount} file(s)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Sync360Surface(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Transfer size",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = formatFileSize(state.totalSizeBytes),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Decline")
            }

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Accept")
            }
        }
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    val unitSize: Long
    val unitName: String

    when {
        sizeBytes >= 1_000_000_000 -> {
            unitSize = 1_000_000_000
            unitName = "GB"
        }

        sizeBytes >= 1_000_000 -> {
            unitSize = 1_000_000
            unitName = "MB"
        }

        sizeBytes >= 1_000 -> {
            unitSize = 1_000
            unitName = "KB"
        }

        else -> return "$sizeBytes bytes"
    }

    val wholePart = sizeBytes / unitSize
    val decimalPart = (sizeBytes % unitSize) * 10 / unitSize

    return if (decimalPart == 0L) {
        "$wholePart $unitName"
    } else {
        "$wholePart.$decimalPart $unitName"
    }
}
