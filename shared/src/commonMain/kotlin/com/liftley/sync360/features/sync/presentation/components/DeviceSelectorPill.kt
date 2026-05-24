package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
// Removed icons import
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus

@Composable
fun DeviceSelectorPill(
    connectionStatus: ConnectionStatus,
    activeDeviceName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val (label, containerColor, contentColor) = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> Triple(
            activeDeviceName ?: "Unknown Device",
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer
        )
        ConnectionStatus.CONNECTING -> Triple(
            "Connecting...",
            colorScheme.tertiaryContainer,
            colorScheme.onTertiaryContainer
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            "Not connected",
            colorScheme.errorContainer,
            colorScheme.onErrorContainer
        )
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
        Text(
            text = "▼",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
