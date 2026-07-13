package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.presentation.app.components.Sync360Surface

@Composable
fun ReceivedTextStateUi(
    text: String,
    onCopyText: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Sync360Surface(containerColor = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                ) {
                    Icon(
                        imageVector = Close,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        contentDescription = "Close Without Copying"
                    )
                    Text("Close Without Copying", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        Button(
            onClick = onCopyText,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Copy text and Close")
        }
    }
}