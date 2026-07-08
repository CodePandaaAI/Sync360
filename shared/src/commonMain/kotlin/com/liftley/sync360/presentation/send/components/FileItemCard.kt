package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.PickedFile

@Composable
fun FileItemCard(file: PickedFile, onRemoveClick: (PickedFile) -> Unit) {
    Sync360Surface(MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onRemoveClick(file) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Icon(imageVector = Close, contentDescription = null)
            }

            Text(
                file.displayName.formatDisplayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun String.formatDisplayName(): String {
    // If the name is already short, don't change anything
    if (this.length <= 20) return this

    // Find the last dot to isolate the extension
    val dotIndex = this.lastIndexOf('.')

    // Edge case: No extension found or dot is at the very beginning/end
    if (dotIndex <= 0 || dotIndex >= this.length - 1) {
        return this.take(20) + "..."
    }

    val nameWithoutExtension = this.substring(0, dotIndex)
    val extension = this.substring(dotIndex) // Includes the dot (e.g., ".pdf")

    // Take the first 20 characters of the name, add ellipsis, and paste the extension
    val truncatedName = nameWithoutExtension.take(20)

    return "$truncatedName...$extension"
}
