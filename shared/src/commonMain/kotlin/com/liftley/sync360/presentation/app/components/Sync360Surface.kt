package com.liftley.sync360.presentation.app.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color


@Composable
fun Sync360Surface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    content: @Composable (() -> Unit)
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = shape
    ) {
        content()
    }
}