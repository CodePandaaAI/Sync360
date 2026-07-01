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
import com.liftley.sync360.presentation.featureSend.model.SendItem
import com.liftley.sync360.presentation.featureSend.model.SendScreenUiState
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val nearbyDevices by sendScreenViewModel.nearbyDevices.collectAsStateWithLifecycle()
    val discoveryStatus by sendScreenViewModel.discoveryServiceStatus.collectAsStateWithLifecycle()
    val sendingItemsList by sendScreenViewModel.sendingItemsList.collectAsStateWithLifecycle()

    val sendScreenUiState by sendScreenViewModel.sendScreenUiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var clipbaordText by remember { mutableStateOf("") }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Selected Items",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (sendingItemsList.isNotEmpty()) {
                        IconButton(
                            onClick = { sendScreenViewModel.clearAllSendItemList() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(imageVector = Close, null)
                        }
                    }
                }

                if (sendingItemsList.isEmpty()) {
                    Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Emoji_Nature, null, modifier = Modifier.size(48.dp))
                            Text("No Items Added")
                        }
                    }
                } else {
                    sendingItemsList.forEach { item ->
                        SendItemCard(item) { sendScreenViewModel.removeSendItem(item) }
                    }
                }
            }
        }
        Sync360Surface {

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    clipbaordText,
                    onValueChange = { clipbaordText = it },
                    label = { Text("Add Text to send") },
                    maxLines = 4,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        sendScreenViewModel.addSendItem(
                            SendItem.Text(clipbaordText)
                        )
                        clipbaordText = ""
                    }
                ) {
                    Text("Add")
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
                            coroutineScope.launch {
                                sendScreenViewModel.onDeviceClick(device)
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
fun SendItemCard(item: SendItem, onClick: () -> Unit) {
    when (item) {
        is SendItem.Text -> {
            Sync360Surface {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.text, style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            onClick = {
                                onClick()
                            }
                        ) {
                            Icon(
                                imageVector = Close,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}