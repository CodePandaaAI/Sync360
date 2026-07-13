package com.liftley.sync360.core.designsystem.canvas

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Preview(showBackground = true)
@Composable
fun AppIcon() {
    val infiniteTransition = rememberInfiniteTransition("tail_Animation")

    val tailAnimation = infiniteTransition.animateFloat(
        -1f,
        1f,
        animationSpec = InfiniteRepeatableSpec(
            tween(2000),
            RepeatMode.Reverse
        )
    )

    val topMovement = infiniteTransition.animateFloat(
        initialValue = 0.99f, 1.05f, animationSpec = InfiniteRepeatableSpec(
            tween(2000),
            RepeatMode.Reverse
        )
    )

    Canvas(modifier = Modifier.size(200.dp)) {
        val tail = Path().apply {
            // 1. Define your start and end points
            val startX = center.x - 70f
            val startY = center.y + 70f
            val endX = center.x + 20f
            val endY = center.y

            // 2. Define the angle you want to pull towards, and how hard to pull it (distance)
            val pullAngleDegrees = tailAnimation.value * 225f
            val pullDistance =
                tailAnimation.value * 50f // How far out to stretch the curve's center

            // 3. Find the midpoint between start and end to use as a base reference
            val midX = (startX + endX) / 2f
            val midY = (startY + endY) / 2f

            // 4. Convert angle to radians
            val radians = Math.toRadians(pullAngleDegrees.toDouble()).toFloat()

            // 5. Calculate the control point by offsetting from the midpoint towards the angle
            val controlX = midX + pullDistance * cos(radians)
            val controlY = midY + pullDistance * sin(radians)

            // 6. Build the path
            moveTo(startX, startY)
            quadraticTo(controlX, controlY, endX, endY)
        }

        drawPath(
            path = tail,
            color = Color.Black,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        val back = Path().apply {
            moveTo(0f, center.y)
            quadraticTo(center.x, center.y, center.x, size.height)

            lineTo(0f, size.height)
            lineTo(0f, center.y)
        }

        drawPath(back, color = Color(0xFF00A4D1), style = Fill)

        drawCircle(
            color = Color(0xFFDF042A),
            radius = 30f,
            center = Offset(
                topMovement.value * center.x + 20f,
                topMovement.value * center.y
            )
        )
    }
}