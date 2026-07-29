package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.SendScreenViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.compose.koinInject
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun FilesSendContent() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val sendScreenState = sendScreenViewModel.screenState.collectAsStateWithLifecycle()
    val viewController = LocalUIViewController.current
    val documentPickerDelegate = remember(sendScreenViewModel) {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>
            ) {
                val selectedUrls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
                if (selectedUrls.isNotEmpty()) {
                    sendScreenViewModel.handleFilesSelected(selectedUrls)
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Selected Files",
            style = MaterialTheme.typography.titleLarge
        )
        if (sendScreenState.value.files.isNotEmpty()) {
            IconButton(
                onClick = sendScreenViewModel::clearSelectedFiles,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(imageVector = Close, contentDescription = null)
            }
        }
    }

    if (sendScreenState.value.files.isNotEmpty()) {
        val files = sendScreenState.value.files

        Sync360Surface(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files) { file ->
                        FileItemCard(file) {
                            sendScreenViewModel.removeSelectedFileFromList(it)
                        }
                    }
                }

                if (files.size > 3) {
                    Text(
                        text = "${files.size} files selected - scroll to view",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilePickerButton(
            text = "Image/Videos",
            startRounded = true,
            modifier = Modifier.weight(1f),
            onClick = {
                val picker = UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(UTTypeImage, UTTypeMovie),
                    asCopy = true
                )
                picker.allowsMultipleSelection = true
                picker.delegate = documentPickerDelegate
                viewController.presentViewController(picker, animated = true, completion = null)
            }
        )
        FilePickerButton(
            text = "Files",
            startRounded = false,
            modifier = Modifier.weight(1f),
            onClick = {
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
}

@Composable
private fun FilePickerButton(
    text: String,
    startRounded: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = if (startRounded) {
            MaterialTheme.shapes.large.copy(
                topStart = CornerSize(24.dp),
                bottomStart = CornerSize(24.dp),
                topEnd = CornerSize(4.dp),
                bottomEnd = CornerSize(4.dp)
            )
        } else {
            MaterialTheme.shapes.large.copy(
                topStart = CornerSize(4.dp),
                bottomStart = CornerSize(4.dp),
                topEnd = CornerSize(24.dp),
                bottomEnd = CornerSize(24.dp)
            )
        },
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
