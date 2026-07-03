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
import com.liftley.sync360.presentation.send.model.TextSendState

@Composable
fun TextSendStatusCard(
    textSendState: TextSendState,
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
            if (textSendState != TextSendState.Idle) {
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

            when (textSendState) {
                TextSendState.Idle -> {
                    Text(
                        "Your sending nothing right now",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is TextSendState.Sending -> {
                    Text(
                        "Sending text to ${textSendState.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is TextSendState.Sent -> {
                    Text(
                        "Text sent to ${textSendState.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is TextSendState.Failed -> {
                    Text(
                        "Text not sent because ${textSendState.reason}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}