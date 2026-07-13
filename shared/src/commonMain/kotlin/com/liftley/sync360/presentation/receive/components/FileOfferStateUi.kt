package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Box(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Sync360Surface(modifier = Modifier.align(Alignment.Center)) {
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
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Accept", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Decline", style = MaterialTheme.typography.titleMedium)
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
