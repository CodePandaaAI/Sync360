package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.domain.model.ClipboardEntry
import com.liftley.sync360.features.sync.domain.model.ConnectionStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceStream
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.model.SyncAsset
import com.liftley.sync360.features.sync.domain.model.SyncTransferState

@Composable
fun NetworkStatusPill(
    connectionStatus: ConnectionStatus,
    localNetworkHealthy: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = when {
        connectionStatus == ConnectionStatus.CONNECTED && localNetworkHealthy -> Color(0xFF22C55E)
        connectionStatus == ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
        else -> colorScheme.error
    }
    val label = when {
        connectionStatus == ConnectionStatus.CONNECTED && localNetworkHealthy -> "Local network"
        connectionStatus == ConnectionStatus.CONNECTED -> "Remote peer"
        connectionStatus == ConnectionStatus.CONNECTING -> "Connecting"
        else -> "Offline"
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DeviceCarousel(
    devices: List<DeviceProfile>,
    activeDeviceId: String?,
    onSwitchDevice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(devices) { device ->
            val active = device.id == activeDeviceId
            DeviceAvatar(
                device = device,
                active = active,
                onClick = { onSwitchDevice(device.id) }
            )
        }
    }
}

@Composable
fun DeviceAvatar(
    device: DeviceProfile,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .widthIn(min = 76.dp, max = 104.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (active) 68.dp else 60.dp)
                .clip(CircleShape)
                .background(if (active) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh)
                .border(
                    width = if (active) 3.dp else 1.dp,
                    color = if (active) colorScheme.primary else colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = deviceToken(device.type),
                style = MaterialTheme.typography.titleMedium,
                color = if (active) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = device.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) colorScheme.primary else colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StreamSections(
    stream: DeviceStream?,
    activeDevice: DeviceProfile?,
    onCopyClipboard: () -> Unit,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
    desktopMode: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (stream == null || activeDevice == null) {
            EmptyStreamState()
        } else {
            StreamHeader(device = activeDevice, stream = stream)
            ClipboardCard(
                clipboard = stream.clipboard,
                onCopy = onCopyClipboard,
                desktopMode = desktopMode
            )
            if (stream.media.isEmpty()) {
                EmptyCategoryCard("Recent media", "No media scanner is connected yet.")
            } else {
                MediaRail(
                    assets = stream.media,
                    onDownload = onDownload
                )
            }
            if (stream.documents.isEmpty()) {
                EmptyCategoryCard("Documents", "No document scanner is connected yet.")
            } else {
                DocumentList(
                    assets = stream.documents,
                    onDownload = onDownload
                )
            }
        }
    }
}

@Composable
fun StreamHeader(device: DeviceProfile, stream: DeviceStream) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = device.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stream.lastSeenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        AssistChip(
            onClick = {},
            label = { Text("${stream.storageUsedPercent}% used") }
        )
    }
}

@Composable
fun ClipboardCard(
    clipboard: ClipboardEntry,
    onCopy: () -> Unit,
    desktopMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Latest clipboard",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    )
                    Text(
                        text = "${clipboard.sourceApp} - ${clipboard.updatedLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
                Button(
                    onClick = onCopy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Copy")
                }
            }
            Surface(
                shape = RoundedCornerShape(if (desktopMode) 18.dp else 22.dp),
                color = colorScheme.surface.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = clipboard.text,
                    modifier = Modifier.padding(16.dp),
                    style = if (desktopMode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = if (desktopMode) 6 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MediaRail(
    assets: List<SyncAsset>,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Recent media", "${assets.size} previews")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(assets) { asset ->
                MediaTile(asset = asset, onDownload = { onDownload(asset.id) })
            }
        }
    }
}

@Composable
fun MediaTile(asset: SyncAsset, onDownload: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val desaturated = asset.syncState == SyncTransferState.THUMBNAIL_ONLY
    Card(
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onDownload),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.linearGradient(
                        colors = if (desaturated) {
                            listOf(colorScheme.surfaceVariant, colorScheme.surfaceContainer)
                        } else {
                            listOf(colorScheme.tertiaryContainer, colorScheme.secondaryContainer)
                        }
                    )
                )
        ) {
            Text(
                text = assetTypeLabel(asset),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            TransferBadge(
                asset = asset,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = asset.title,
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = asset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (asset.syncState == SyncTransferState.DOWNLOADING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun DocumentList(
    assets: List<SyncAsset>,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Documents", "On demand")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                assets.forEachIndexed { index, asset ->
                    DocumentRow(asset = asset, onDownload = { onDownload(asset.id) })
                    if (index != assets.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentRow(asset: SyncAsset, onDownload: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = assetTypeLabel(asset),
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = asset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TransferBadge(asset = asset)
    }
}

@Composable
fun TransferBadge(asset: SyncAsset, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val (label, color, content) = when (asset.syncState) {
        SyncTransferState.THUMBNAIL_ONLY -> Triple("DL", colorScheme.surface.copy(alpha = 0.9f), colorScheme.primary)
        SyncTransferState.DOWNLOADING -> Triple("${asset.progressPercent}%", colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        SyncTransferState.FULLY_DOWNLOADED -> Triple("OK", colorScheme.primary, colorScheme.onPrimary)
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SectionTitle(title: String, actionLabel: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStreamState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No device stream selected",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyCategoryCard(title: String, message: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, "Not wired")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deviceToken(type: DeviceType): String = when (type) {
    DeviceType.DESKTOP -> "PC"
    DeviceType.PHONE -> "PH"
    DeviceType.TABLET -> "TB"
}

private fun assetTypeLabel(asset: SyncAsset): String = when (asset.type) {
    com.liftley.sync360.features.sync.domain.model.SyncAssetType.IMAGE -> "IMG"
    com.liftley.sync360.features.sync.domain.model.SyncAssetType.VIDEO -> "VID"
    com.liftley.sync360.features.sync.domain.model.SyncAssetType.PDF -> "PDF"
    com.liftley.sync360.features.sync.domain.model.SyncAssetType.DOCUMENT -> "DOC"
    com.liftley.sync360.features.sync.domain.model.SyncAssetType.ARCHIVE -> "ZIP"
}
