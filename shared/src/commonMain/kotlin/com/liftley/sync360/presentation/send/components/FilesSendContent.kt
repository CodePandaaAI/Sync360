package com.liftley.sync360.presentation.send.components

import androidx.compose.runtime.Composable
import com.liftley.sync360.domain.model.SelectedFile

@Composable
expect fun FilesSendContent(
    files: List<SelectedFile>,
    onFilesSelected: (List<Any>) -> Unit,
    onClearFiles: () -> Unit,
    onRemoveFile: (SelectedFile) -> Unit
)

internal expect fun filePreviewModel(file: SelectedFile): Any
