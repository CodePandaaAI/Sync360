package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType

@Composable
fun NearbyDevicesSection(
    title: String,
    nearbyDevices: List<DeviceProfile>,
    pairedDeviceIds: Set<String>,
    isScanning: Boolean,
    localIp: String,
    onConnect: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleNearby = nearbyDevices.filter { it.id !in pairedDeviceIds }
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Premium OneUI rounded corner
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Your IP: $localIp",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                
                if (isScanning && visibleNearby.isEmpty()) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary.copy(alpha = 0.15f * pulseAlpha),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colorScheme.primary
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isScanning, onClick = onRefresh)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Wifi else Icons.Default.Refresh,
                                contentDescription = "Scan",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (visibleNearby.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isScanning) {
                            "Scanning local network for devices…"
                        } else {
                            "No devices found. Open Sync360 on another device connected to $localIp."
                        },
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    visibleNearby.forEach { device ->
                        NearbyDeviceRow(
                            device = device,
                            onConnect = { onConnect(device.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyDeviceRow(
    device: DeviceProfile,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        shape = RoundedCornerShape(20.dp), // Spacious OneUI squircle row
        color = colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp), // Premium Squircle shape
                color = colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = deviceIcon(device.type),
                        contentDescription = null,
                        tint = colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.hostAddress ?: device.id,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.primary,
                modifier = Modifier.clip(CircleShape)
            ) {
                Text(
                    text = "Connect",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ScanningStatusChip(
    nearbyCount: Int,
    isScanning: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = !isScanning, onClick = onRefresh),
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (nearbyCount > 0) "$nearbyCount nearby" else "Scanning…",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deviceIcon(type: DeviceType): ImageVector = when (type) {
    DeviceType.DESKTOP -> Icons.Default.Computer
    DeviceType.PHONE -> Icons.Default.Smartphone
    DeviceType.TABLET -> Icons.Default.Tablet
}
