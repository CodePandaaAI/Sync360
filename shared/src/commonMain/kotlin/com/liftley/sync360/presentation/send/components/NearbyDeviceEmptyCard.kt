package com.liftley.sync360.presentation.send.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.liftley.sync360.domain.model.DiscoveryStatus

@Preview
@Composable
fun NearbyDeviceEmptyCard(
    status: DiscoveryStatus = DiscoveryStatus.Idle,
    onReloadClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition("Loading Animation")

    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Restart
        ),
        label = "Loading Progress"
    )

    val startAngle = progress * 360f

    val sweepAngle = (progress - 1f) * 360f


    val title = when (status) {
        DiscoveryStatus.Idle -> "No Device Found"
        DiscoveryStatus.Starting -> "Starting Discovery"
        DiscoveryStatus.Running -> "Looking for Devices"
        DiscoveryStatus.Stopping -> "Stopping Discovery"
    }

    val subtitle = when (status) {
        DiscoveryStatus.Idle -> "Tap reload to scan again"
        DiscoveryStatus.Starting -> "Preparing nearby scan"
        DiscoveryStatus.Running -> "Keep both devices on the same Wi-Fi"
        DiscoveryStatus.Stopping -> "Cleaning up current scan"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(
                enabled = status == DiscoveryStatus.Idle,
                onClick = onReloadClick
            )
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (status == DiscoveryStatus.Running) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    drawArc(
                        topLeft = center + Offset(x = -24f, y = -24f),
                        color = Color(0xFF4C662B),
                        startAngle = startAngle,
                        useCenter = false,
                        sweepAngle = sweepAngle,
                        size = Size(48f, 48f),
                        style = Stroke(10f, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}