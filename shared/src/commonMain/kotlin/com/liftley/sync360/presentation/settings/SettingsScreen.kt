package com.liftley.sync360.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.presentation.app.components.NetworkRepairAction
import com.liftley.sync360.presentation.app.components.Sync360Surface

@Composable
fun SettingsScreen(
    repairEnabled: Boolean,
    onRepairClick: () -> Unit
) {
    Sync360Surface(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            NetworkRepairAction(
                enabled = repairEnabled,
                onRepairClick = onRepairClick
            )
        }
    }
}
