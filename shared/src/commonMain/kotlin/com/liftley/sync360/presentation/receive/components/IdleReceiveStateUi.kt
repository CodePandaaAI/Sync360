package com.liftley.sync360.presentation.receive.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.presentation.app.components.FileReceiveCodeCard

@Composable
fun IdleReceiveStateUi(
    fileReceiveCode: String,
    registrationStatus: RegistrationStatus,
    onStartNetworkServices: () -> Unit,
    onStopNetworkServices: () -> Unit,
    onRetryNetworkServices: () -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        YouWillAppearAs()

        FileReceiveCodeCard(
            fileReceiveCode = fileReceiveCode
        )

        NearbyVisibilityCard(
            registrationStatus = registrationStatus,
            onStartNetworkServices = onStartNetworkServices,
            onStopNetworkServices = onStopNetworkServices,
            onRetryNetworkServices = onRetryNetworkServices
        )
    }
}
