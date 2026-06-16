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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Sync360Surface(cornerRadius = 24.dp) {
            Column(
                modifier = Modifier.padding(if (isDesktop) 20.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DeviceGlyph(activeDevice.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Send files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "To ${activeDevice.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (uiState.selectedFiles.isEmpty()) {
                    FilePickerActions(onEvent = onEvent)
                } else {
                    SelectedFilesPanel(
                        files = uiState.selectedFiles,
                        onSend = { onEvent(SyncEvent.SendSelectedFiles) },
                        onClear = { onEvent(SyncEvent.ClearSelectedFiles) },
                        onAddFiles = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Any)) }
                    )
                }
            }
        }

        Sync360Surface(cornerRadius = 24.dp) {
            Column(
                modifier = Modifier.padding(if (isDesktop) 18.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Text clipboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Paste current clipboard or copy text",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = uiState.outgoingText,
                    onValueChange = { onEvent(SyncEvent.UpdateOutgoingText(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Paste copied text") },
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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onEvent(SyncEvent.PasteFromClipboard) },
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Send", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilePickerActions(onEvent: (SyncEvent) -> Unit) {
    FileAction(
        label = "Select Files",
        icon = Icons.Default.Folder,
        onClick = { onEvent(SyncEvent.OpenFilePicker(FilePickerKind.Any)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FileAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        cornerRadius = 24.dp,
        color = colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colorScheme.primaryContainer
            ) {
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(26.dp))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SelectedFilesPanel(
    files: List<PickedFile>,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onAddFiles: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colorScheme.surfaceContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TransferSummaryRow(
            title = "${files.size} file${if (files.size == 1) "" else "s"} selected",
            subtitle = formatBytes(files.sumOf { it.sizeBytes }),
            files = files
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onAddFiles, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add files")
            }
            OutlinedButton(onClick = onClear, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear files")
            }
            Button(onClick = onSend, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Send", fontWeight = FontWeight.Bold)
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
        Surface(shape = RoundedCornerShape(18.dp), color = colorScheme.primaryContainer) {
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
