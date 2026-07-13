package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.core.designsystem.icons.Send
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.SendOperationState

@Composable
fun SendOperationStateUi(
    state: SendOperationState,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    when (state) {
        SendOperationState.Idle -> Unit

        is SendOperationState.SendingTextOffer -> {
            SendingOperationUi(
                title = "Sending text",
                message = "Waiting for ${state.deviceName} to accept",
                onCancel = onCancel
            )
        }

        is SendOperationState.SendingFileOffer -> {
            SendingOperationUi(
                title = "Sending file offer",
                message = "Waiting for ${state.deviceName} to accept " +
                        fileCountMessage(state.fileCount),
                onCancel = onCancel
            )
        }

        is SendOperationState.SendingFile -> {
            val completedFiles = state.fileNumber - 1
            val progress = completedFiles.toFloat() / state.totalFiles.toFloat()

            SendingOperationUi(
                title = "Sending files",
                message = "Sending to ${state.deviceName}",
                detail = "${state.fileNumber} / ${state.totalFiles}: ${state.fileName}",
                progress = progress,
                onCancel = onCancel
            )
        }

        is SendOperationState.TextSent -> {
            SendResultUi(
                title = "Text sent",
                message = "Sent successfully to ${state.deviceName}",
                wasSuccessful = true,
                onDone = onDone
            )
        }

        is SendOperationState.FilesSent -> {
            SendResultUi(
                title = "Files sent",
                message = "${fileCountMessage(state.fileCount)} sent to ${state.deviceName}",
                wasSuccessful = true,
                onDone = onDone
            )
        }

        SendOperationState.Cancelled -> {
            SendResultUi(
                title = "Sending cancelled",
                message = "The active send was stopped",
                wasSuccessful = false,
                onDone = onDone
            )
        }

        is SendOperationState.OperationFailed -> {
            SendResultUi(
                title = "Could not send",
                message = state.reason,
                wasSuccessful = false,
                onDone = onDone
            )
        }
    }
}

@Composable
private fun SendingOperationUi(
    title: String,
    message: String,
    detail: String? = null,
    progress: Float? = null,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Sync360Surface(modifier = Modifier.align(Alignment.Center)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun SendResultUi(
    title: String,
    message: String,
    wasSuccessful: Boolean,
    onDone: () -> Unit
) {
    Box(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        Sync360Surface(
            modifier = Modifier
                .align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Icon(
                        imageVector = if (wasSuccessful) Send else Close,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).padding(8.dp),
                        tint = if (wasSuccessful) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Done")
        }
    }
}

private fun fileCountMessage(fileCount: Int): String {
    return if (fileCount == 1) {
        "1 file"
    } else {
        "$fileCount files"
    }
}
