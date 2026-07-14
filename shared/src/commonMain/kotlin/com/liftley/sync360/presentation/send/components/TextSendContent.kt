package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Close

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
        Text(
            "Selected Text",
            style = MaterialTheme.typography.titleLarge
        )
        if (textInput.isNotEmpty()) {
            IconButton(
                onClick = onClearText,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(imageVector = Close, contentDescription = null)
            }
        }
    }

    OutlinedTextField(
        value = textInput,
        onValueChange = onTextChange,
        label = { Text("Add text to send") },
        maxLines = 5,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    )
}
