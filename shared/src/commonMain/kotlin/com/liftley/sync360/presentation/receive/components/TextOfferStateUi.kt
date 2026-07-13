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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState

@Composable
fun TextOfferStateUi(
    state: ReceiveScreenState.IncomingTextOffer,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Sync360Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    state.senderDeviceName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Wants to send text (${state.characterCount} characters)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            state.preview.ifBlank { "No preview available" },
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(),
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