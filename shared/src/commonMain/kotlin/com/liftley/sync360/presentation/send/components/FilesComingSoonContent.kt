package com.liftley.sync360.presentation.send.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.core.designsystem.icons.Emoji_Nature
import com.liftley.sync360.presentation.app.components.Sync360Surface

@Composable
fun FilesComingSoonContent() {
    Sync360Surface(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Emoji_Nature, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(
                "File sending is not ready yet",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}