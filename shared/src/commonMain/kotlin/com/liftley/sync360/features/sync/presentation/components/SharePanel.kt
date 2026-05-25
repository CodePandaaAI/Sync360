package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.presentation.SyncEvent
import com.liftley.sync360.features.sync.presentation.SyncUiState

@Composable
fun SharePanel(
    isDesktop: Boolean,
    uiState: SyncUiState,
    activeDevice: DeviceProfile,
    onEvent: (SyncEvent) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isDesktop) 24.dp else 28.dp),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(if (isDesktop) 20.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceGlyph(activeDevice.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = activeDevice.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ready to share",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = uiState.outgoingText,
                onValueChange = { onEvent(SyncEvent.UpdateOutgoingText(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type or paste text") },
                minLines = if (isDesktop) 3 else 2,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outlineVariant,
                    focusedContainerColor = colorScheme.surfaceContainer,
                    unfocusedContainerColor = colorScheme.surfaceContainer
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onEvent(SyncEvent.PasteFromClipboard) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paste", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onEvent(SyncEvent.SendMessage(uiState.outgoingText)) },
                    enabled = uiState.outgoingText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send", fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

            if (uiState.selectedFiles.isEmpty()) {
                FilePickerActions(onEvent = onEvent)
            } else {
                SelectedFilesPanel(
                    files = uiState.selectedFiles,
                    onSend = { onEvent(SyncEvent.SendSelectedFiles) },
                    onClear = { onEvent(SyncEvent.ClearSelectedFiles) },
                    onAddMedia = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Media)) },
                    onAddFiles = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Any)) }
                )
            }
        }
    }
}

@Composable
private fun FilePickerActions(onEvent: (SyncEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FileAction(
            label = "Images / videos",
            icon = Icons.Default.PermMedia,
            onClick = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Media)) },
            modifier = Modifier.weight(1f)
        )
        FileAction(
            label = "Files",
            icon = Icons.Default.Folder,
            onClick = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Any)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FileAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SelectedFilesPanel(
    files: List<PickedFile>,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onAddMedia: () -> Unit,
    onAddFiles: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TransferSummaryRow(
                title = "${files.size} file${if (files.size == 1) "" else "s"} selected",
                subtitle = formatBytes(files.sumOf { it.sizeBytes }),
                files = files
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onAddMedia, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                FilledTonalButton(onClick = onAddFiles, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                OutlinedButton(onClick = onClear, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Button(onClick = onSend, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1.4f)) {
                    Text("Send", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TransferPreviewSummaryRow(
    title: String,
    subtitle: String,
    files: List<PickedFilePreview>,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = colorScheme.primaryContainer) {
            Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (files.any { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") }) {
                        Icons.Default.PermMedia
                    } else {
                        Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

typealias PickedFilePreview = com.liftley.sync360.features.sync.domain.model.TransferFilePreview

@Composable
private fun TransferSummaryRow(
    title: String,
    subtitle: String,
    files: List<PickedFile>
) {
    TransferPreviewSummaryRow(
        title = title,
        subtitle = subtitle,
        files = files.map { PickedFilePreview(it.name, it.mimeType, it.sizeBytes) }
    )
}

@Composable
private fun DeviceGlyph(name: String) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toInt() / 10.0} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${(mb * 10).toInt() / 10.0} MB"
    val gb = mb / 1024.0
    return "${(gb * 10).toInt() / 10.0} GB"
}
