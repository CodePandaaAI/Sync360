package com.liftley.sync360.presentation.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.Spacing

@Composable
fun NetworkRepairAction(
    enabled: Boolean,
    onRepairClick: () -> Unit,
) {
    Sync360Surface(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(
                text = "Connection repair",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Use this if nearby devices cannot discover this device or fail to connect after the network changes.\n\nRepair restarts local discovery and advertises Sync360 again. It does not remove received files or reset the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRepairClick,
                enabled = enabled
            ) {
                Text("Repair connection")
            }
        }
    }
}
