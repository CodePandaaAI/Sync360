package com.liftley.sync360.presentation.featureSend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.presentation.viewmodel.NavigationViewModel
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        val viewModel = koinInject<SendScreenViewModel>()
        val navigationViewModel = koinInject<NavigationViewModel>()

        val nearbyDevices by viewModel.nearbyDevices.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            nearbyDevices.forEach { device ->
                Surface {
                    Column {
                        Text(device.id, modifier = Modifier.padding(16.dp))
                        Text(device.deviceName, modifier = Modifier.padding(16.dp))
                        Text(device.deviceType, modifier = Modifier.padding(16.dp))
                        Text(device.protocolVersion, modifier = Modifier.padding(16.dp))
                        Text("${device.port}", modifier = Modifier.padding(16.dp))
                        Text(device.serviceName, modifier = Modifier.padding(16.dp))
                        Text(device.serviceType, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}