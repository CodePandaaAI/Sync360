package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Download
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState

@Composable
fun ReceivedFilesStateUi(
    state: ReceiveScreenState.ReceivedFiles,
    onOpenDownloads: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Sync360Surface(modifier = Modifier.align(Alignment.Center)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Icon(
                        imageVector = Download,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = receivedFilesMessage(state.fileCount),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Saved to Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Received from ${state.senderDeviceName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Done")
            }

            OutlinedButton(
                onClick = onOpenDownloads,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Download,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("Open Downloads")
            }
        }
    }
}

private fun receivedFilesMessage(fileCount: Int): String {
    return if (fileCount == 1) {
        "1 file received"
    } else {
        "$fileCount files received"
    }
}
