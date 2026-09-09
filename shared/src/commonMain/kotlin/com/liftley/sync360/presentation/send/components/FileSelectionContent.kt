package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.presentation.app.components.Sync360Surface

private val ImageFileExtensions = setOf(
    "avif", "bmp", "gif", "heic", "heif", "jpeg", "jpg", "png", "webp"
)

// Fixed size tiers — never smaller than Compact, never bigger than Expanded.
private val CompactThumbnailSize = 128.dp   // phones
private val MediumThumbnailSize = 160.dp   // tablets (portrait) / small desktop windows
private val ExpandedThumbnailSize = 192.dp // tablets (landscape) / desktop

private fun thumbnailSizeFor(maxWidth: Dp): Dp = when {
    maxWidth < 600.dp -> CompactThumbnailSize
    maxWidth < 1024.dp -> MediumThumbnailSize
    else -> ExpandedThumbnailSize
}

@Composable
internal fun FileSelectionContent(
    files: List<SelectedFile>,
    onClearFiles: () -> Unit,
    onRemoveFile: (SelectedFile) -> Unit,
    onPickMedia: (() -> Unit)?,
    onPickFiles: () -> Unit
) {
    BoxWithConstraints {
        val thumbnailSize = thumbnailSizeFor(maxWidth)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Files to send", style = MaterialTheme.typography.titleLarge)
                if (files.isNotEmpty()) {
                    TextButton(onClick = onClearFiles) {
                        Text("Clear all")
                    }
                }
            }

            if (files.isNotEmpty()) {
                Sync360Surface(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${files.size} ${if (files.size == 1) "file" else "files"} selected",
                            style = MaterialTheme.typography.titleMedium
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = files, key = { it.uri }) { file ->
                                SelectedFilePreview(
                                    file = file,
                                    onRemoveFile = onRemoveFile,
                                    size = thumbnailSize
                                )
                            }
                        }
                    }
                }
            }

            FilePickerActions(
                hasSelectedFiles = files.isNotEmpty(),
                onPickMedia = onPickMedia,
                onPickFiles = onPickFiles
            )
        }
    }
}

@Composable
internal fun SelectedFilePreview(
    file: SelectedFile,
    onRemoveFile: (file: SelectedFile) -> Unit,
    size: Dp,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (file.isImageFile()) {
            AsyncImage(
                model = filePreviewModel(file),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = fileTypeLabel(file),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Filename overlaid at the bottom, scrim behind it — same pattern as the recents view.
        Text(
            text = truncateKeepingExtension(file.displayName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        IconButton(
            onClick = { onRemoveFile(file) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(24.dp)
        ) {
            Icon(
                imageVector = Close,
                contentDescription = "Remove ${file.displayName}"
            )
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
            Sync360Surface(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.extraLarge).clickable {
                    onPickMedia()
                }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Select Media",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Photos · Videos",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Sync360Surface(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.extraLarge).clickable {
                    onPickFiles()
                }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Select docs", maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Pdf · Docs · Excel",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Sync360Surface(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.extraLarge).clickable {
                    onPickFiles()
                }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (hasSelectedFiles) "Add more files" else "Select files",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Pdf · Docs · Excel",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun SelectedFile.isImageFile(): Boolean {
    if (mimeType?.startsWith("image/") == true) return true
    return displayName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase() in ImageFileExtensions
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

private fun truncateKeepingExtension(name: String, maxBaseChars: Int = 12): String {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex <= 0 || dotIndex == name.length - 1) return name // no real extension

    val base = name.substring(0, dotIndex)
    val extension = name.substring(dotIndex) // includes the dot, e.g. ".pdf"

    return if (base.length > maxBaseChars) {
        "${base.take(maxBaseChars)}…$extension"
    } else {
        name
    }
}