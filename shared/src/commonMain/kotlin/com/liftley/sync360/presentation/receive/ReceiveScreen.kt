package com.liftley.sync360.presentation.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Emoji_Nature
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import com.liftley.sync360.presentation.app.components.Sync360Surface
import org.koin.compose.koinInject

@Composable
fun ReceiveScreen() {
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = receiveScreenState) {
                    ReceiveScreenState.Idle -> {
                        IdleReceiveState()
                    }

                    is ReceiveScreenState.IncomingTextOffer -> {
                        TextOfferState(
                            state = state,
                            onAccept = { receiveScreenViewModel.makeDecision(UserDecision.ACCEPTED) },
                            onDecline = { receiveScreenViewModel.makeDecision(UserDecision.DECLINED) }
                        )
                    }

                    is ReceiveScreenState.ReceivedText -> {
                        ReceivedTextState(
                            text = state.text,
                            onCopyText = {
                                receiveScreenViewModel.copyReceivedText(state.text)
                                receiveScreenViewModel.clearState()
                            },
                            onClear = {
                                receiveScreenViewModel.clearState()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleReceiveState() {
    Sync360Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Emoji_Nature,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Nothing to receive right now",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Keep Sync360 open on nearby devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TextOfferState(
    state: ReceiveScreenState.IncomingTextOffer,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Sync360Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Incoming text",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                "${state.senderName} wants to send text",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                "${state.characterCount} characters",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        state.preview.ifBlank { "No preview available" },
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
private fun ReceivedTextState(
    text: String,
    onCopyText: () -> Unit,
    onClear: () -> Unit
) {
    Sync360Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Received text",
                style = MaterialTheme.typography.titleLarge
            )

            Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Text(
                    text,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }

                Button(
                    onClick = onCopyText,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy text")
                }
            }
        }
    }
}