package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry

@Composable
fun ClipboardHistorySection(
    textsList: List<ClipboardEntry>,
    onCopyClick: (ClipboardEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val received = textsList.take(3)

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Recent clipboard",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (received.isEmpty()) {
            Sync360Surface(color = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Received clipboard text will appear here.",
                    modifier = Modifier.padding(Spacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                received.forEach { clipboard ->
                    ClipboardItemCard(
                        clipboard = clipboard,
                        onCopyClick = onCopyClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardItemCard(
    clipboard: ClipboardEntry,
    onCopyClick: (ClipboardEntry) -> Unit
) {
    Sync360Surface(
        modifier = Modifier
            .clickable { onCopyClick(clipboard) },
        cornerRadius = 24.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.md,
                vertical = Spacing.sm + Spacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.md)
            ) {
                Text(
                    text = clipboard.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = clipboard.senderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy to clipboard",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Spacing.md)
                    )
                }
            }
        }
    }
}
