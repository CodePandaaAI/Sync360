package com.liftley.sync360.presentation.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.liftley.sync360.core.designsystem.Spacing
import com.liftley.sync360.domain.model.FileTransferProgress

@Composable
fun FileTransferProgressUi(
    progress: FileTransferProgress,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "${progress.percentage}%",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
