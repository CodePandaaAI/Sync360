package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.presentation.app.components.Sync360Surface

@Composable
fun NearbyVisibilityCard(
    registrationStatus: RegistrationStatus,
    onStartNetworkServices: () -> Unit,
    onStopNetworkServices: () -> Unit,
    onRetryNetworkServices: () -> Unit
) {
    val statusText = when (registrationStatus) {
        RegistrationStatus.Idle -> "Not visible to nearby devices 😎"
        RegistrationStatus.Starting -> "Becoming visible...☕"
        RegistrationStatus.FailedToStart -> "Couldn’t become visible 🤧"
        RegistrationStatus.Running -> "Visible to nearby devices 🌞"
        RegistrationStatus.Stopping -> "Becoming hidden...☕"
        RegistrationStatus.CleanupFailed -> "Couldn’t become hidden 🤧"
    }

    val buttonLabel = when (registrationStatus) {
        RegistrationStatus.Idle -> "Start"
        RegistrationStatus.Starting -> "Starting…"
        RegistrationStatus.Running -> "Stop"
        RegistrationStatus.Stopping -> "Stopping…"
        RegistrationStatus.FailedToStart,
        RegistrationStatus.CleanupFailed -> "Try again"
    }

    val onButtonClick: (() -> Unit)? = when (registrationStatus) {
        RegistrationStatus.Idle -> onStartNetworkServices
        RegistrationStatus.Running -> onStopNetworkServices
        RegistrationStatus.FailedToStart,
        RegistrationStatus.CleanupFailed -> onRetryNetworkServices

        RegistrationStatus.Starting,
        RegistrationStatus.Stopping -> null
    }

    Text(text = "Nearby visibility", modifier = Modifier.padding(horizontal = 16.dp))

    Sync360Surface(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = statusText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

            OutlinedButton(
                onClick = { onButtonClick?.invoke() },
                enabled = onButtonClick != null
            ) {
                Text(buttonLabel)
            }
        }
    }
}