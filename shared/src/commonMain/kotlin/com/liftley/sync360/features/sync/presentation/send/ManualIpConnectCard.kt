package com.liftley.sync360.features.sync.presentation.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liftley.sync360.features.sync.presentation.components.Sync360Surface
@Composable
internal fun ManualIpConnectCard(onConnect: (String) -> Unit) {
    var manualHost by rememberSaveable { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme
    Sync360Surface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connect manually",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("IP address") },
                    shape = RoundedCornerShape(24.dp)
                )
                Button(
                    onClick = {
                        onConnect(manualHost)
                        manualHost = ""
                    },
                    enabled = manualHost.isNotBlank(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(56.dp).padding(top = 8.dp)
                ) {
                    Text("Add")
                }
            }
        }
    }
}
