package com.liftley.sync360

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.domain.repository.NetworkServices
import jdk.internal.net.http.common.Log
import org.koin.compose.koinInject

@Preview(showBackground = true)
@Composable
fun App() {
    Sync360Theme {
        Scaffold { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                val networkServices = koinInject<NetworkServices>()
                LaunchedEffect(Unit) {
                    networkServices.startNetworkServices()
                }

                val nearbyDevices by networkServices.nearbyDevices.collectAsStateWithLifecycle()


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
    }
}