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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.liftley.sync360.presentation.featureSend.model.SendScreenState
import com.liftley.sync360.presentation.featureSend.model.SendTab
import com.liftley.sync360.presentation.featureSend.model.TextSendState
import com.liftley.sync360.presentation.model.NearbyDeviceUiModel
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val screenState by sendScreenViewModel.screenState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextSendStatusCard(
            textSendState = screenState.textSendState,
            onClear = sendScreenViewModel::resetTextSendState
        )

        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecondaryTabRow(selectedTabIndex = screenState.selectedTab.ordinal) {
                    SendTab.entries.forEach { tab ->
                        Tab(
                            selected = screenState.selectedTab == tab,
                            onClick = { sendScreenViewModel.onTabSelected(tab) },
                            text = { Text(tab.name) }
                        )
                    }
                }

                when (screenState.selectedTab) {
                    SendTab.Text -> {
                        TextSendContent(
                            textInput = screenState.textInput,
                            onTextChange = sendScreenViewModel::onTextChanged,
                            onClearText = { sendScreenViewModel.onTextChanged("") }
                        )
                    }

                    SendTab.Files -> {
                        FilesComingSoonContent()
                    }
                }
            }
        }

        NearbyDevicesSection(
            screenState = screenState,
            onReloadClick = sendScreenViewModel::restartDiscoveryServices,
            onDeviceClick = { device ->
                if (screenState.selectedTab == SendTab.Text) {
                    coroutineScope.launch {
                        sendScreenViewModel.sendTextToDevice(device.id)
                    }
                }
            }
        )
    }
}

@Composable
private fun TextSendStatusCard(
    textSendState: TextSendState,
    onClear: () -> Unit
) {
    Sync360Surface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (textSendState != TextSendState.Idle) {
                IconButton(
                    onClick = onClear,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Close,
                        contentDescription = null
                    )
                }
            }

            when (textSendState) {
                TextSendState.Idle -> {
                    Text(
                        "Your sending nothing right now",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is TextSendState.Sending -> {
                    Text(
                        "Sending text to ${textSendState.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is TextSendState.Sent -> {
                    Text(
                        "Text sent to ${textSendState.deviceName}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                is TextSendState.Failed -> {
                    Text(
                        "Text not sent because ${textSendState.reason}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSendContent(
    textInput: String,
    onTextChange: (String) -> Unit,
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
            if (textInput.isNotEmpty()) {
                IconButton(
                    onClick = onClearText,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Close, contentDescription = null)
                }
            }
        }

        if (textInput.isBlank()) {
            Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Emoji_Nature, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text("No text added")
                }
            }
        } else {
            TextItemCard(textInput)
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = onTextChange,
            label = { Text("Add text to send") },
            maxLines = 4,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        )
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
            Icon(imageVector = Emoji_Nature, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(
                "File sending is not ready yet",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun NearbyDevicesSection(
    screenState: SendScreenState,
    onReloadClick: () -> Unit,
    onDeviceClick: (NearbyDeviceUiModel) -> Unit
) {
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
                    enabled = screenState.discoveryStatus == DiscoveryStatus.Idle,
                    onClick = onReloadClick
                ) {
                    Icon(
                        imageVector = Reload,
                        contentDescription = null
                    )
                }
            }

            screenState.nearbyDevices.forEach { device ->
                NearbyDeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) }
                )
            }

            NearbyDeviceEmptyCard(
                status = screenState.discoveryStatus,
                onReloadClick = onReloadClick
            )
        }
    }
}