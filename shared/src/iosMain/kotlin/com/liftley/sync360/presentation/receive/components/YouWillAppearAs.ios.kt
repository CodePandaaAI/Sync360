package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.presentation.app.components.Sync360Surface
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun YouWillAppearAs() {
    val deviceName = remember {
        UIDevice.currentDevice.name
            .trim()
            .ifBlank { UIDevice.currentDevice.model }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("You'll appear as")

        Spacer(modifier = Modifier.height(16.dp))

        Sync360Surface(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
