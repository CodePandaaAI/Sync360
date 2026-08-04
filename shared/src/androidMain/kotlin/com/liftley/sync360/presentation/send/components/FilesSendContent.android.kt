package com.liftley.sync360.presentation.send.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.liftley.sync360.domain.model.SelectedFile

@Composable
actual fun FilesSendContent(
    files: List<SelectedFile>,
    onFilesSelected: (List<Any>) -> Unit,
    onClearFiles: () -> Unit,
    onRemoveFile: (SelectedFile) -> Unit
) {
    val multipleMediaPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            onFilesSelected(uris)
        }

    val openDocuments =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            onFilesSelected(uris)
        }

    FileSelectionContent(
        files = files,
        onClearFiles = onClearFiles,
        onRemoveFile = onRemoveFile,
        onPickMedia = {
            multipleMediaPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        },
        onPickFiles = { openDocuments.launch(arrayOf("*/*")) }
    )
}

internal actual fun filePreviewModel(file: SelectedFile): Any = file.uri
