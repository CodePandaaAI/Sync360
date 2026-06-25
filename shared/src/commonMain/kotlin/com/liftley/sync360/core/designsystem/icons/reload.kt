package com.liftley.sync360.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val Reload: ImageVector
  get() {
    if (_sync != null) {
      return _sync!!
    }
    _sync =
      ImageVector.Builder(
          name = "sync",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(4f, 20f)
            verticalLineTo(18f)
            horizontalLineTo(6.75f)
            lineTo(6.35f, 17.65f)
            quadTo(5.05f, 16.5f, 4.53f, 15.03f)
            reflectiveQuadTo(4f, 12.05f)
            quadTo(4f, 9.27f, 5.66f, 7.11f)
            reflectiveQuadTo(10f, 4.25f)
            verticalLineToRelative(2.1f)
            quadTo(8.2f, 7f, 7.1f, 8.56f)
            reflectiveQuadTo(6f, 12.05f)
            quadToRelative(0f, 1.13f, 0.43f, 2.19f)
            reflectiveQuadTo(7.75f, 16.2f)
            lineTo(8f, 16.45f)
            verticalLineTo(14f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineTo(4f)
            close()
            moveTo(14f, 19.75f)
            verticalLineToRelative(-2.1f)
            quadTo(15.8f, 17f, 16.9f, 15.44f)
            reflectiveQuadTo(18f, 11.95f)
            quadTo(18f, 10.83f, 17.58f, 9.76f)
            reflectiveQuadTo(16.25f, 7.8f)
            lineTo(16f, 7.55f)
            verticalLineTo(10f)
            horizontalLineTo(14f)
            verticalLineTo(4f)
            horizontalLineToRelative(6f)
            verticalLineTo(6f)
            horizontalLineTo(17.25f)
            lineToRelative(0.4f, 0.35f)
            quadToRelative(1.22f, 1.23f, 1.79f, 2.66f)
            reflectiveQuadTo(20f, 11.95f)
            quadToRelative(0f, 2.78f, -1.66f, 4.94f)
            reflectiveQuadTo(14f, 19.75f)
            close()
          }
        }
        .build()
    return _sync!!
  }

private var _sync: ImageVector? = null
