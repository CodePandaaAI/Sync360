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
import java.awt.FileDialog
import java.awt.Frame

@Composable
actual fun FilesSendContent() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val sendScreenState = sendScreenViewModel.screenState.collectAsStateWithLifecycle()

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

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        onClick = {
            val selectedFiles = FileDialog(
                null as Frame?,
                "Select files",
                FileDialog.LOAD
            ).run {
                isMultipleMode = true
                isVisible = true
                files.toList()
            }

            if (selectedFiles.isNotEmpty()) {
                sendScreenViewModel.handleFilesSelected(selectedFiles)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Choose Files",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
