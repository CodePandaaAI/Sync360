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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Reload
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.brandComponents.Sync360Surface
import com.liftley.sync360.presentation.featureSend.components.NearbyDeviceCard
import com.liftley.sync360.presentation.featureSend.components.NearbyDeviceEmptyCard
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    val viewModel = koinInject<SendScreenViewModel>()
    val nearbyDevices by viewModel.nearbyDevices.collectAsStateWithLifecycle()
    val discoveryStatus by viewModel.discoveryServiceStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
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
                        enabled = discoveryStatus == DiscoveryStatus.Idle,
                        onClick = {
                            viewModel.restartDiscoveryServices()
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
                        }
                    )
                }

                NearbyDeviceEmptyCard(
                    status = discoveryStatus,
                    onReloadClick = {
                        viewModel.restartDiscoveryServices()
                    }
                )
            }
        }
    }
}