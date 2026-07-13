package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState

@Composable
fun ReceivingFilesStateUi(
    state: ReceiveScreenState.ReceivingFiles
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = state.senderDeviceName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Saving received files to Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LinearProgressIndicator(
                    progress = {
                        if (state.fileCount == 0) {
                            0f
                        } else {
                            state.completedFileCount.toFloat() / state.fileCount
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "${state.completedFileCount} / ${state.fileCount} files saved",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
