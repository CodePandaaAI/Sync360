package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.presentation.SyncUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopDevicePickerDialog(
    uiState: SyncUiState,
    onDismissRequest: () -> Unit,
    onSelectDevice: (DeviceProfile) -> Unit
) {
    val pairedIds = uiState.connectedDevices.map { it.id }.toSet()
    val nearby = uiState.nearbyDevices.filter { it.id !in pairedIds }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(420.dp),
        title = {
            Text("Devices on your network", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your IP: ${uiState.serverIp}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (nearby.isEmpty() && uiState.connectedDevices.isEmpty()) {
                    Text(
                        "Searching for nearby devices… Keep Sync360 open on another device on the same Wi‑Fi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (nearby.isNotEmpty()) {
                        Text(
                            "Nearby",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(nearby, key = { it.id }) { device ->
                                DeviceRow(device = device, action = "Connect") {
                                    onSelectDevice(device)
                                }
                            }
                        }
                    }
                    if (uiState.connectedDevices.isNotEmpty()) {
                        Text(
                            "Paired this session",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.connectedDevices, key = { it.id }) { device ->
                                DeviceRow(device = device, action = "Open") {
                                    onSelectDevice(device)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DeviceRow(
    device: DeviceProfile,
    action: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (device.type == DeviceType.DESKTOP) Icons.Default.Computer else Icons.Default.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = device.hostAddress ?: device.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
