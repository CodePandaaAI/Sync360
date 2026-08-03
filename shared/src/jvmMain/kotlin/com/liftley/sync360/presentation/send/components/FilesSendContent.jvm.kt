package com.liftley.sync360.presentation.send.components

import androidx.compose.runtime.Composable
import com.liftley.sync360.domain.model.SelectedFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun FilesSendContent(
    files: List<SelectedFile>,
    onFilesSelected: (List<Any>) -> Unit,
    onClearFiles: () -> Unit,
    onRemoveFile: (SelectedFile) -> Unit
) {
    FileSelectionContent(
        files = files,
        onClearFiles = onClearFiles,
        onRemoveFile = onRemoveFile,
        onPickMedia = null,
        onPickFiles = {
            val dialog = FileDialog(
                null as Frame?,
                "Select files",
                FileDialog.LOAD
            )
            val selectedFiles = try {
                dialog.isMultipleMode = true
                dialog.isVisible = true
                dialog.files.toList()
            } finally {
                dialog.dispose()
            }

            if (selectedFiles.isNotEmpty()) {
                onFilesSelected(selectedFiles)
            }
        }
    )
}

internal actual fun filePreviewModel(file: SelectedFile): Any = File(file.uri)
