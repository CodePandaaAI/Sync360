package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.FileSendState

@Composable
fun FileSendStatusCard(
    fileSendState: FileSendState,
    onClear: () -> Unit
) {
    Sync360Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (fileSendState is FileSendState.OfferAccepted || fileSendState is FileSendState.OperationFailed) {
                IconButton(
                    onClick = onClear,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Close,
                        contentDescription = null
                    )
                }
            }

            when (fileSendState) {
                FileSendState.Idle -> {
                    Text(
                        "Your sending nothing right now",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is FileSendState.SendingOffer -> {
                    Text(
                        "Sending file offer to ${fileSendState.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is FileSendState.OfferAccepted -> {
                    Text(
                        "Offer Accepted by ${fileSendState.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is FileSendState.OperationFailed -> {
                    Text(
                        fileSendState.reason,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}