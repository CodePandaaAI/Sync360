package com.liftley.sync360.presentation.featureSend.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.liftley.sync360.core.designsystem.icons.Close
import com.liftley.sync360.presentation.featureSend.model.SendItem
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface

@Composable
fun SendItemCard(item: SendItem, onClick: () -> Unit) {
    when (item) {
        is SendItem.Text -> {
            Sync360Surface {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.text, style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            onClick = {
                                onClick()
                            }
                        ) {
                            Icon(
                                imageVector = Close,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

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