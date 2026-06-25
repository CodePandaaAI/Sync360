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
public val Android: ImageVector
  get() {
    if (_android != null) {
      return _android!!
    }
    _android =
      ImageVector.Builder(
          name = "android",
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
            moveTo(1f, 18f)
            quadTo(1.23f, 15.33f, 2.64f, 13.08f)
            reflectiveQuadTo(6.4f, 9.5f)
            lineTo(4.55f, 6.3f)
            quadTo(4.4f, 6.07f, 4.48f, 5.82f)
            reflectiveQuadTo(4.8f, 5.45f)
            quadTo(5f, 5.32f, 5.25f, 5.4f)
            reflectiveQuadToRelative(0.4f, 0.3f)
            lineTo(7.5f, 8.9f)
            quadTo(9.65f, 8f, 12f, 8f)
            reflectiveQuadToRelative(4.5f, 0.9f)
            lineTo(18.35f, 5.7f)
            quadTo(18.5f, 5.47f, 18.75f, 5.4f)
            reflectiveQuadTo(19.2f, 5.45f)
            quadToRelative(0.25f, 0.13f, 0.32f, 0.38f)
            reflectiveQuadTo(19.45f, 6.3f)
            lineTo(17.6f, 9.5f)
            quadToRelative(2.35f, 1.32f, 3.76f, 3.57f)
            reflectiveQuadTo(23f, 18f)
            horizontalLineTo(1f)
            close()
            moveTo(7.89f, 14.89f)
            quadTo(8.25f, 14.53f, 8.25f, 14f)
            reflectiveQuadTo(7.89f, 13.11f)
            reflectiveQuadTo(7f, 12.75f)
            reflectiveQuadTo(6.11f, 13.11f)
            quadTo(5.75f, 13.48f, 5.75f, 14f)
            reflectiveQuadToRelative(0.36f, 0.89f)
            reflectiveQuadTo(7f, 15.25f)
            reflectiveQuadTo(7.89f, 14.89f)
            close()
            moveToRelative(10f, 0f)
            quadTo(18.25f, 14.53f, 18.25f, 14f)
            reflectiveQuadTo(17.89f, 13.11f)
            quadTo(17.53f, 12.75f, 17f, 12.75f)
            quadToRelative(-0.52f, 0f, -0.89f, 0.36f)
            quadTo(15.75f, 13.48f, 15.75f, 14f)
            reflectiveQuadToRelative(0.36f, 0.89f)
            reflectiveQuadTo(17f, 15.25f)
            quadToRelative(0.53f, 0f, 0.89f, -0.36f)
            close()
          }
        }
        .build()
    return _android!!
  }

private var _android: ImageVector? = null
