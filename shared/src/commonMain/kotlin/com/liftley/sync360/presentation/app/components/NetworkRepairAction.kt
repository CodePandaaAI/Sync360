package com.liftley.sync360.presentation.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.liftley.sync360.core.designsystem.Spacing

@Composable
fun NetworkRepairAction(
    enabled: Boolean,
    onRepairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "If devices cannot discover you or fail to connect, repair the local connection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        OutlinedButton(
            onClick = onRepairClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Repair connection")
        }
    }
}
