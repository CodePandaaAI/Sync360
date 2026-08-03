package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.presentation.app.components.Sync360Surface
import kotlin.math.roundToInt

private val ImageFileExtensions = setOf(
    "avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "webp"
)
private val FileSizeUnits = listOf("B", "KB", "MB", "GB", "TB")

@Composable
internal fun FileSelectionContent(
    files: List<SelectedFile>,
    onClearFiles: () -> Unit,
    onRemoveFile: (SelectedFile) -> Unit,
    onPickMedia: (() -> Unit)?,
    onPickFiles: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ready to send", style = MaterialTheme.typography.titleLarge)
            if (files.isNotEmpty()) {
                TextButton(onClick = onClearFiles) {
                    Text("Clear all")
                }
            }
        }

        if (files.isEmpty()) {
            Sync360Surface(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Choose photos, videos, or other files to send.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            SelectedFilesCard(
                files = files,
                onRemoveFile = onRemoveFile
            )
        }

        FilePickerActions(
            hasSelectedFiles = files.isNotEmpty(),
            onPickMedia = onPickMedia,
            onPickFiles = onPickFiles
        )
    }
}

@Composable
private fun SelectedFilesCard(
    files: List<SelectedFile>,
    onRemoveFile: (SelectedFile) -> Unit
) {
    Sync360Surface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "${files.size} ${if (files.size == 1) "file" else "files"} selected",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatTotalFileSize(files),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SelectedFilesRow(
                files = files,
                onRemoveFile = onRemoveFile
            )
        }
    }
}

@Composable
private fun SelectedFilesRow(
    files: List<SelectedFile>,
    onRemoveFile: (SelectedFile) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = files,
            key = { file -> file.uri }
        ) { file ->
            FileItemCard(
                file = file,
                onRemoveClick = onRemoveFile,
                modifier = Modifier.width(136.dp)
            )
        }
    }
}

@Composable
internal fun SelectedFilePreview(
    file: SelectedFile,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Sync360Surface(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = fileTypeLabel(file),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )

            if (file.isImageFile()) {
                AsyncImage(
                    model = filePreviewModel(file),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun FilePickerActions(
    hasSelectedFiles: Boolean,
    onPickMedia: (() -> Unit)?,
    onPickFiles: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onPickMedia != null) {
            FilledTonalButton(
                onClick = onPickMedia,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (hasSelectedFiles) "Add media" else "Photos & videos",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onPickFiles,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (hasSelectedFiles) "Add files" else "Other files",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            FilledTonalButton(
                onClick = onPickFiles,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasSelectedFiles) "Add more files" else "Choose files")
            }
        }
    }
}

internal fun formatSelectedFileSize(sizeBytes: Long?): String =
    sizeBytes?.let(::formatFileSize) ?: "Size unavailable"

private fun formatTotalFileSize(files: List<SelectedFile>): String {
    var totalBytes = 0L
    files.forEach { file ->
        totalBytes += file.sizeBytes ?: return "Total size unavailable"
    }
    return "${formatFileSize(totalBytes)} total"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < FileSizeUnits.lastIndex) {
        value /= 1024
        unitIndex++
    }

    val rounded = (value * 10).roundToInt() / 10.0
    val displayValue = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
    return "$displayValue ${FileSizeUnits[unitIndex]}"
}

private fun SelectedFile.isImageFile(): Boolean {
    if (mimeType?.startsWith("image/") == true) return true

    return displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase() in
        ImageFileExtensions
}

private fun fileTypeLabel(file: SelectedFile): String {
    val extension = file.displayName.substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isNotBlank()) return extension.take(4).uppercase()

    return when {
        file.mimeType?.startsWith("video/") == true -> "VIDEO"
        file.mimeType?.startsWith("audio/") == true -> "AUDIO"
        else -> "FILE"
    }
}
