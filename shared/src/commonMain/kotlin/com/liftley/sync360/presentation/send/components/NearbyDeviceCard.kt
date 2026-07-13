package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Android
import com.liftley.sync360.presentation.send.model.NearbyDeviceUiModel

@Preview
@Composable
fun NearbyDeviceCard(
    device: NearbyDeviceUiModel = NearbyDeviceUiModel(
        id = "uuid-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        deviceName = "Living Room TV",
        deviceType = "Smart TV",
        protocolVersion = "v2.4.1",
        hostAddresses = listOf("192.168.1.45", "fe80::1ff:fe23:4567:890a"),
        port = 8080,
        fileTransferPort = 0,
        serviceName = "Chromecast-Ultra-Stream",
        serviceType = "_googlecast._tcp.local."
    ),
    onClick: () -> Unit = {}
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Android,
                contentDescription = null
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "IP and Port :${device.hostAddresses.first()}:${device.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}