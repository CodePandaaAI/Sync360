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
public val Desktop: ImageVector
  get() {
    if (_desktop_windows != null) {
      return _desktop_windows!!
    }
    _desktop_windows =
      ImageVector.Builder(
          name = "desktop_windows",
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
            moveTo(8f, 21f)
            verticalLineTo(19f)
            horizontalLineToRelative(2f)
            verticalLineTo(17f)
            horizontalLineTo(4f)
            quadTo(3.18f, 17f, 2.59f, 16.41f)
            reflectiveQuadTo(2f, 15f)
            verticalLineTo(5f)
            quadTo(2f, 4.17f, 2.59f, 3.59f)
            reflectiveQuadTo(4f, 3f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 5f)
            verticalLineTo(15f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 17f)
            horizontalLineTo(14f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            horizontalLineTo(8f)
            close()
            moveTo(4f, 15f)
            horizontalLineTo(20f)
            verticalLineTo(5f)
            horizontalLineTo(4f)
            verticalLineTo(15f)
            close()
            moveToRelative(0f, 0f)
            verticalLineTo(5f)
            verticalLineTo(15f)
            close()
          }
        }
        .build()
    return _desktop_windows!!
  }

private var _desktop_windows: ImageVector? = null
