package com.liftley.sync360.core.designsystem.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true, name = "Doraemon")
@Composable
private fun Character() {
    Character(
        modifier = Modifier.size(
            width = 250.dp,
            height = 300.dp
        )
    )
}

@Composable
fun Character(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
    ) {
        val designWidth = 600f
        val designHeight = 700f

        val drawingScale = minOf(
            size.width / designWidth,
            size.height / designHeight
        )

        scale(
            scaleX = drawingScale,
            scaleY = drawingScale,
            pivot = Offset.Zero
        ) {
            // Center of the original 600 x 700 drawing coordinates.
            val center = Offset(300f, 275f)

        // The Blue Arc behind
        drawArc(
            color = Color(0xFF00A4D1), startAngle = 0f,
            sweepAngle = 360f,
            useCenter = true,
            topLeft = Offset(center.x - 300f, center.y - 275f),
            size = Size(600f, 550f),
            style = Fill
        )

        // The Border Around Blue
        drawArc(
            color = Color.Black,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = true,
            topLeft = Offset(center.x - 300f, center.y - 275f),
            size = Size(600f, 550f),
            style = Stroke(2f)
        )

        // Inner White Arc
        drawArc(
            color = Color.White,
            startAngle = 0f,
            style = Fill,
            sweepAngle = 360f,
            useCenter = true,
            topLeft = Offset(center.x - 262.5f, center.y - 199f),
            size = Size(525f, 475f),
        )

        // Inner White Arc Border
        drawArc(
            color = Color.Black,
            startAngle = 0f,
            style = Stroke(3f),
            sweepAngle = 360f,
            useCenter = true,
            topLeft = Offset(center.x - 262.5f, center.y - 199f),
            size = Size(525f, 475f),
        )

        // Left Eye Fill
        drawArc(
            color = Color.White,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - 120f, center.y - 250f),
            size = Size(120f, 140f),
            style = Fill // Fills the inside
        )

        // Left Eye Border
        drawArc(
            color = Color.Black,
            useCenter = false,
            topLeft = Offset(center.x, center.y - 250f),
            startAngle = 0f,
            sweepAngle = 360f,
            size = Size(120f, 140f),
            style = Stroke(1f)
        )

        val eyeWidth = 40f
        val eyeMaxHeight = 80f

        val eyeTopLeft = Offset(
            x = center.x - 80f,
            y = center.y - 200f
        )

        drawOval(
            color = Color.Black,
            topLeft = eyeTopLeft,
            size = Size(eyeWidth, eyeMaxHeight),
            style = Fill
        )

        drawOval(
            color = Color.White,
            topLeft = Offset(
                x = center.x - 70f,
                y = center.y - 160f
            ),
            size = Size(10f, 20f),
            style = Fill
        )

        // Right Eye Fill
        drawArc(
            color = Color.White,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x, center.y - 250f),
            size = Size(120f, 140f),
            style = Fill // Fills the inside
        )

        // Right Eye Border
        drawArc(
            color = Color.Black,
            useCenter = false,
            topLeft = Offset(center.x - 120f, center.y - 250f),
            startAngle = 0f,
            sweepAngle = 360f,
            size = Size(120f, 140f),
            style = Stroke(1f)
        )

        // Right Eye Closed Inner
        drawArc(
            color = Color.Black,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x + 20f, center.y - 200f),
            size = Size(80f, 80f),
            style = Stroke(4f)
        )

        // Nose
        drawCircle(
            color = Color.Red,
            radius = 45f,
            center = Offset(center.x, center.y - 90f)
        )

        // Nose Inner Part
        drawCircle(
            color = Color.White,
            radius = 10f,
            center = Offset(center.x - 10f, center.y - 90f)
        )

        // Left Much
        drawLine(
            color = Color.Black,
            start = Offset(center.x - 100f, center.y - 50f),
            end = Offset(center.x - 275f, center.y - 150f),
            strokeWidth = 4f
        )

        drawLine(
            color = Color.Black,
            start = Offset(center.x - 100f, center.y),
            end = Offset(center.x - 275f, center.y - 50f),
            strokeWidth = 4f
        )

        drawLine(
            color = Color.Black,
            start = Offset(center.x - 100f, center.y + 50f),
            end = Offset(center.x - 275f, center.y + 50f),
            strokeWidth = 4f
        )

        // Right much
        drawLine(
            color = Color.Black,
            start = Offset(x = center.x + 100f, y = center.y - 50f),
            end = Offset(x = center.x + 275f, y = center.y - 150f),
            strokeWidth = 4f
        )

        drawLine(
            color = Color.Black,
            start = Offset(center.x + 100f, center.y),
            end = Offset(center.x + 275f, center.y - 50f),
            strokeWidth = 4f

        )

        drawLine(
            color = Color.Black,
            start = Offset(center.x + 100f, center.y + 50f),
            end = Offset(center.x + 275f, center.y + 50f),
            strokeWidth = 4f
        )

        val mouthPath = Path().apply {
            moveTo(center.x - 225f, center.y + 100f)
            quadraticTo(
                center.x, center.y + 40f,
                center.x + 225f, center.y + 100f
            )
            // Lower mouth curve
            quadraticTo(
                center.x, center.y + 425f,
                center.x - 225f, center.y + 100f
            )

            close()
        }
        drawPath(mouthPath, Color(0xFFCE0729))

        val tonguePath = Path().apply {
            moveTo(center.x - 160f, center.y + 180f)
            quadraticTo(
                center.x, center.y + 345f,
                center.x + 160f, center.y + 180f
            )

            quadraticTo(
                center.x, center.y + 60f,
                center.x - 160f, center.y + 180f
            )
        }

            drawPath(tonguePath, Color(0xFFF35502))
        }
    }
}
