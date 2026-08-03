package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TextSendContent(
    textInput: String,
    onTextChange: (String) -> Unit,
    onClearText: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Text to send", style = MaterialTheme.typography.titleLarge)
        if (textInput.isNotEmpty()) {
            TextButton(onClick = onClearText) {
                Text("Clear", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = textInput,
            onValueChange = onTextChange,
            label = { Text("Message") },
            placeholder = { Text("Type or paste text here") },
            minLines = 5,
            maxLines = 5,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${textInput.length} characters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
