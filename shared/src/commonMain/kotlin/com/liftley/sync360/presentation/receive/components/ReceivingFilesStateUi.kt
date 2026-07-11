package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState

@Composable
fun ReceivingFilesStateUi(
    state: ReceiveScreenState.ReceivingFiles
) {
    Sync360Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Receiving files",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "${state.senderDeviceName} is sending " +
                    "${state.fileCount} file(s)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
