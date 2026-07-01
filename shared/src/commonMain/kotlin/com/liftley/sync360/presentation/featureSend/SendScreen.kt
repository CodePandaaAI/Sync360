package com.liftley.sync360.presentation.featureSend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.core.designsystem.icons.Emoji_Nature
import com.liftley.sync360.core.designsystem.icons.Reload
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.featureSend.components.NearbyDeviceCard
import com.liftley.sync360.presentation.featureSend.components.NearbyDeviceEmptyCard
import com.liftley.sync360.presentation.featureSend.components.TextItemCard
import com.liftley.sync360.presentation.featureSend.model.SendScreenUiState
import com.liftley.sync360.presentation.featureSend.model.SendTab
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val nearbyDevices by sendScreenViewModel.nearbyDevices.collectAsStateWithLifecycle()
    val discoveryStatus by sendScreenViewModel.discoveryServiceStatus.collectAsStateWithLifecycle()

    val sendScreenUiState by sendScreenViewModel.sendScreenUiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var clipboardTextFieldText by remember { mutableStateOf("") }
    var clipboardText by remember { mutableStateOf("") }

    var selectedTab by remember { mutableStateOf(SendTab.Text) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (sendScreenUiState != SendScreenUiState.Idle) {
                    IconButton(
                        onClick = {
                            sendScreenViewModel.resetState()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Close, null
                        )
                    }
                }
                when (val state = sendScreenUiState) {
                    is SendScreenUiState.Idle -> {
                        Text(
                            "Your Sending Nothing Right Now",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    is SendScreenUiState.Sending -> {
                        Text(
                            "Sending ${state.data} Request To ${state.sendingTo}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    is SendScreenUiState.Sent -> {
                        Text(
                            "Request ${state.data} Sent Successfully to ${state.sentTo}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    is SendScreenUiState.NotSent -> {
                        Text(
                            "Request Rejected because ${state.reason}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    SendTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.name) }
                        )
                    }
                }

                when (selectedTab) {
                    SendTab.Text -> {
                        TextSendContent(
                            clipboardText = clipboardText,
                            clipboardTextFieldText = clipboardTextFieldText,
                            onClipboardTextChange = { clipboardTextFieldText = it },
                            onAddText = {
                                clipboardText = clipboardTextFieldText
                            },
                            onClearText = {
                                clipboardText = ""
                            }
                        )
                    }

                    SendTab.Files -> {
                        FilesComingSoonContent()
                    }
                }
            }
        }

        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Nearby Devices",
                        style = MaterialTheme.typography.titleLarge
                    )

                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.height(48.dp),
                        enabled = discoveryStatus == DiscoveryStatus.Idle,
                        onClick = {
                            sendScreenViewModel.restartDiscoveryServices()
                        }
                    ) {
                        Icon(
                            imageVector = Reload,
                            contentDescription = null
                        )
                    }
                }

                nearbyDevices.forEach { device ->
                    NearbyDeviceCard(
                        device = device,
                        onClick = {
                            if (selectedTab == SendTab.Text) {
                                coroutineScope.launch {
                                    sendScreenViewModel.onDeviceClickForTextTransfer(
                                        device,
                                        clipboardText
                                    )
                                }
                            }
                        }
                    )
                }

                NearbyDeviceEmptyCard(
                    status = discoveryStatus,
                    onReloadClick = {
                        sendScreenViewModel.restartDiscoveryServices()
                    }
                )
            }
        }
    }
}

@Composable
private fun TextSendContent(
    clipboardText: String,
    clipboardTextFieldText: String,
    onClipboardTextChange: (String) -> Unit,
    onAddText: () -> Unit,
    onClearText: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Selected Text",
                style = MaterialTheme.typography.titleLarge
            )
            if (clipboardText.isNotEmpty()) {
                IconButton(
                    onClick = onClearText,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Close, null)
                }
            }
        }

        if (clipboardText.isEmpty()) {
            Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Emoji_Nature, null, modifier = Modifier.size(48.dp))
                    Text("No Text Added")
                }
            }
        } else {
            TextItemCard(clipboardText)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                clipboardTextFieldText,
                onValueChange = { onClipboardTextChange(it) },
                label = { Text("Add Text to send") },
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onAddText
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun FilesComingSoonContent() {
    Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Emoji_Nature, null, modifier = Modifier.size(48.dp))
            Text(
                "File sending is not ready yet",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
