package com.liftley.sync360.presentation.send.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.liftley.sync360.domain.model.SelectedFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FilesSendContent(
    files: List<SelectedFile>,
    onFilesSelected: (List<Any>) -> Unit,
    onClearFiles: () -> Unit,
    onRemoveFile: (SelectedFile) -> Unit
) {
    val viewController = LocalUIViewController.current
    val currentOnFilesSelected = rememberUpdatedState(onFilesSelected)
    val documentPickerDelegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>
            ) {
                val selectedUrls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
                if (selectedUrls.isNotEmpty()) {
                    currentOnFilesSelected.value(selectedUrls)
                }
            }
        }
    }

    FileSelectionContent(
        files = files,
        onClearFiles = onClearFiles,
        onRemoveFile = onRemoveFile,
        onPickMedia = {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeImage, UTTypeMovie),
                asCopy = true
            )
            picker.allowsMultipleSelection = true
            picker.delegate = documentPickerDelegate
            viewController.presentViewController(picker, animated = true, completion = null)
        },
        onPickFiles = {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeItem),
                asCopy = true
            )
            picker.allowsMultipleSelection = true
            picker.delegate = documentPickerDelegate
            viewController.presentViewController(picker, animated = true, completion = null)
        }
    )
}

internal actual fun filePreviewModel(file: SelectedFile): Any = file.uri
