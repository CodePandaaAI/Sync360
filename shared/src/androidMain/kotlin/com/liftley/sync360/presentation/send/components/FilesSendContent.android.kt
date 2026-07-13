package com.liftley.sync360.presentation.send.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.SendScreenViewModel
import org.koin.compose.koinInject

@Composable
actual fun FilesSendContent() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val sendScreenState = sendScreenViewModel.screenState.collectAsStateWithLifecycle()

    val multipleMediaPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            sendScreenViewModel.handleFilesSelected(uris)
        }

    val openDocuments =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            sendScreenViewModel.handleFilesSelected(uris)
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
                onClick = { sendScreenViewModel.clearSelectedFiles() },
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
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large.copy(
                topStart = CornerSize(24.dp),
                bottomStart = CornerSize(24.dp),
                topEnd = CornerSize(4.dp),
                bottomEnd = CornerSize(4.dp)
            ),
            onClick = { multipleMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Image/Videos", style = MaterialTheme.typography.titleMedium)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large.copy(
                topStart = CornerSize(4.dp),
                bottomStart = CornerSize(4.dp),
                topEnd = CornerSize(24.dp),
                bottomEnd = CornerSize(24.dp)
            ),
            onClick = { openDocuments.launch(arrayOf("*/*")) },
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Files", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}