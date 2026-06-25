package com.liftley.sync360.presentation.featureSend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Android
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    val viewModel = koinInject<SendScreenViewModel>()

    val nearbyDevices by viewModel.nearbyDevices.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Nearby Devices", style = MaterialTheme.typography.headlineSmall)
                if (nearbyDevices.isEmpty()) {
                    NearbyDeviceEmptyCard()
                } else {
                    nearbyDevices.forEach { device ->
                        NearbyDeviceCard(device.deviceName) {}
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun NearbyDeviceCard(deviceName: String = "Oneplus Nord CE 5", onClick: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = Android, contentDescription = null)
            Text(deviceName, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview
@Composable
fun NearbyDeviceEmptyCard() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No Device Found",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}