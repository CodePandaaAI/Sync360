package com.liftley.sync360.features.sync.presentation.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.SendItem
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.features.sync.presentation.components.formatBytes
@Composable
internal fun SelectedGridItemTile(
    item: SendItem,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isText = item is SendItem.Text
    val name = when (item) {
        is SendItem.File -> item.file.name
        is SendItem.Text -> "Text snippet"
    }
    val id = when (item) {
        is SendItem.File -> item.file.id
        is SendItem.Text -> item.id
    }
    val subtext = when (item) {
        is SendItem.File -> formatBytes(item.file.sizeBytes)
        is SendItem.Text -> item.text
    }

    Surface(
        modifier = Modifier
            .width(180.dp)
            .height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (item is SendItem.File && item.file.mimeType.startsWith("image/")) {
                    coil3.compose.AsyncImage(
                        model = item.file.id,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isText) Icons.AutoMirrored.Filled.Article else Icons.Default.Description,
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onEvent(SyncEvent.RemoveSelectedItem(id)) },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
