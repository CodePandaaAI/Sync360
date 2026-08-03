package com.liftley.sync360.presentation.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.components.FilesSendContent
import com.liftley.sync360.presentation.send.components.NearbyDevicesSection
import com.liftley.sync360.presentation.send.components.SendOperationStateUi
import com.liftley.sync360.presentation.send.components.TextSendContent
import com.liftley.sync360.presentation.send.model.SendOperationState
import com.liftley.sync360.presentation.send.model.SendTab
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SendScreen(
    onTroubleshootClick: () -> Unit
) {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val screenState by sendScreenViewModel.screenState.collectAsStateWithLifecycle()

    when (screenState.sendOperationState) {
        SendOperationState.Idle -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Sync360Surface(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ButtonGroup(
                            overflowIndicator = { menuState ->
                                ButtonGroupDefaults.OverflowIndicator(menuState)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            toggleableItem(
                                checked = screenState.selectedTab == SendTab.Text,
                                label = "Text",
                                onCheckedChange = { checked ->
                                    if (checked) sendScreenViewModel.onTabSelected(SendTab.Text)
                                },
                                weight = 1f
                            )

                            toggleableItem(
                                checked = screenState.selectedTab == SendTab.Files,
                                label = "Files",
                                onCheckedChange = { checked ->
                                    if (checked) sendScreenViewModel.onTabSelected(SendTab.Files)
                                },
                                weight = 1f
                            )
                        }

                        when (screenState.selectedTab) {
                            SendTab.Text -> {
                                TextSendContent(
                                    textInput = screenState.textInput,
                                    onTextChange = { sendScreenViewModel.onTextChanged(it) },
                                    onClearText = { sendScreenViewModel.onTextChanged("") }
                                )
                            }

                            SendTab.Files -> {
                                FilesSendContent(
                                    files = screenState.files,
                                    onFilesSelected = sendScreenViewModel::handleFilesSelected,
                                    onClearFiles = sendScreenViewModel::clearSelectedFiles,
                                    onRemoveFile = sendScreenViewModel::removeSelectedFileFromList
                                )
                            }
                        }
                    }
                }

                NearbyDevicesSection(
                    screenState = screenState,
                    onReloadClick = sendScreenViewModel::restartDiscoveryServices,
                    onDeviceClick = { device -> sendScreenViewModel.onDeviceSelected(device.id) }
                )

                Button(
                    onClick = sendScreenViewModel::sendToSelectedDevice,
                    enabled = screenState.canSend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(screenState.sendButtonLabel)
                }

                TextButton(onClick = onTroubleshootClick) {
                    Text("Troubleshoot")
                }
            }
        }

        else -> {
            SendOperationStateUi(
                state = screenState.sendOperationState,
                onDone = sendScreenViewModel::clearSendOperation,
                onCancel = sendScreenViewModel::cancelSend
            )
        }
    }
}
