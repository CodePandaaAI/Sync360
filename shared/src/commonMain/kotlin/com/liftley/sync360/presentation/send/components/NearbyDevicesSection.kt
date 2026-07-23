package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Reload
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.NearbyDeviceUiModel
import com.liftley.sync360.presentation.send.model.SendScreenState

@Composable
fun NearbyDevicesSection(
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
