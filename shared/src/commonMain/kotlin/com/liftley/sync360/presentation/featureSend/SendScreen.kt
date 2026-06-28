package com.liftley.sync360.presentation.featureSend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.core.designsystem.icons.Reload
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface
import com.liftley.sync360.presentation.featureSend.components.NearbyDeviceCard
import com.liftley.sync360.presentation.featureSend.components.NearbyDeviceEmptyCard
import com.liftley.sync360.presentation.featureSend.model.SendScreenUiState
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