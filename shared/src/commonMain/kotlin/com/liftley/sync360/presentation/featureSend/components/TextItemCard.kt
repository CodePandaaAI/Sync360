package com.liftley.sync360.presentation.featureSend.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface

@Composable
fun TextItemCard(item: String) {

    Sync360Surface {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(item, style = MaterialTheme.typography.titleMedium)
        }
    }
}