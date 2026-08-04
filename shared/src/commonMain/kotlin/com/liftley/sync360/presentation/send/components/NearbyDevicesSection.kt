package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.NearbyDeviceUiModel
import com.liftley.sync360.presentation.send.model.SendScreenState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NearbyDevicesSection(
    screenState: SendScreenState,
    onReloadClick: () -> Unit,
    onDeviceClick: (NearbyDeviceUiModel) -> Unit
) {
    val reloadEnabled =
        screenState.discoveryStatus == DiscoveryStatus.Idle &&
            screenState.registrationStatus == RegistrationStatus.Running

    Sync360Surface(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
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
                    "Nearby devices",
                    style = MaterialTheme.typography.titleLarge
                )

                if (screenState.discoveryStatus == DiscoveryStatus.Running) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            "Scanning",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        enabled = reloadEnabled,
                        onClick = onReloadClick
                    ) {
                        Icon(
                            imageVector = Reload,
                            contentDescription = "Scan again"
                        )
                    }
                }
            }

            if (screenState.nearbyDevices.isNotEmpty()) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    screenState.nearbyDevices.forEach { device ->
                        NearbyDeviceCard(
                            device = device,
                            selected = screenState.selectedDeviceId == device.id,
                            onClick = { onDeviceClick(device) }
                        )
                    }
                }
            }

            if (screenState.nearbyDevices.isEmpty()) {
                NearbyDeviceEmptyCard(
                    status = screenState.discoveryStatus,
                    reloadEnabled = reloadEnabled,
                    onReloadClick = onReloadClick
                )
            }
        }
    }
}
