package com.liftley.sync360.features.sync.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Sync360Surface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    fillMaxWidth: Boolean = true,
    content: @Composable () -> Unit
) {
    val sizedModifier = if (fillMaxWidth) modifier.fillMaxWidth() else modifier
    androidx.compose.material3.Surface(
        modifier = sizedModifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        content = content
    )
}

@Composable
fun Sync360TopBarTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun Sync360IconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = colors
    ) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurface)
    }
}
