package com.liftley.sync360.presentation.brandComponents

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

@Composable
fun Sync360Surface(content: @Composable (() -> Unit)) {
    Surface(
        shape = MaterialTheme.shapes.large
    ) {
        content()
    }
}