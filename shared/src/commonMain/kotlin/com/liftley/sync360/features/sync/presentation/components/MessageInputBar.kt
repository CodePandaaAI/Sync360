package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
// Removed icons import
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MessageInputBar(
    enabled: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            placeholder = { 
                Text(
                    "Message...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ) 
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge,
            enabled = enabled
        )

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSendMessage(text)
                    text = ""
                }
            },
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (enabled && text.isNotBlank()) colorScheme.primary 
                    else colorScheme.surfaceVariant
                )
        ) {
            Text(
                text = "➤",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled && text.isNotBlank()) colorScheme.onPrimary 
                       else colorScheme.onSurfaceVariant
            )
        }
    }
}
