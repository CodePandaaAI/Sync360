package com.liftley.sync360.presentation.presentationComponents

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun Sync360Surface(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable (() -> Unit)
) {
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large
    ) {
        content()
    }
}