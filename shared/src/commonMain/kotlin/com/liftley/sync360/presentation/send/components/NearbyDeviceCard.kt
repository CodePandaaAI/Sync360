package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Android
import com.liftley.sync360.core.designsystem.icons.Desktop
import com.liftley.sync360.core.designsystem.icons.Tv
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.model.NearbyDeviceUiModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview
@Composable
fun NearbyDeviceCard(
    device: NearbyDeviceUiModel = NearbyDeviceUiModel(
        id = "uuid-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        deviceName = "Living Room TV",
        deviceType = "Tv",
        protocolVersion = "v2.4.1",
        hostAddresses = listOf("192.168.1.45", "fe80::1ff:fe23:4567:890a"),
        port = 8080,
        fileTransferPort = 0,
        serviceName = "Chromecast-Ultra-Stream",
        serviceType = "_googlecast._tcp.local."
    ),
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Sync360Surface(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraExtraLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Sync360Surface(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val deviceIcon = when (device.deviceType) {
                    "Android" -> Android
                    "Tv" -> Tv
                    else -> Desktop
                }
                Icon(
                    imageVector = deviceIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp).padding(8.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Available nearby",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = selected,
                onClick = null
            )
        }
    }
}
