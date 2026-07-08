package com.liftley.sync360.presentation.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.send.components.FilesSendContent
import com.liftley.sync360.presentation.send.components.NearbyDevicesSection
import com.liftley.sync360.presentation.send.components.TextSendContent
import com.liftley.sync360.presentation.send.components.TextSendStatusCard
import com.liftley.sync360.presentation.send.model.SendTab
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SendScreen() {
    val sendScreenViewModel = koinInject<SendScreenViewModel>()
    val screenState by sendScreenViewModel.screenState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextSendStatusCard(
            textSendState = screenState.textSendState,
            onClear = sendScreenViewModel::resetTextSendState
        )

        Sync360Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SecondaryTabRow(selectedTabIndex = screenState.selectedTab.ordinal) {
                    SendTab.entries.forEach { tab ->
                        Tab(
                            selected = screenState.selectedTab == tab,
                            onClick = { sendScreenViewModel.onTabSelected(tab) },
                            text = { Text(tab.name) }
                        )
                    }
                }

                when (screenState.selectedTab) {
                    SendTab.Text -> {
                        TextSendContent(
                            textInput = screenState.textInput,
                            onTextChange = sendScreenViewModel::onTextChanged,
                            onClearText = { sendScreenViewModel.onTextChanged("") }
                        )
                    }

                    SendTab.Files -> {
                        FilesSendContent()
                    }
                }
            }
        }

        NearbyDevicesSection(
            screenState = screenState,
            onReloadClick = sendScreenViewModel::restartDiscoveryServices,
            onDeviceClick = { device ->
                if (screenState.selectedTab == SendTab.Text) {
                    coroutineScope.launch {
                        sendScreenViewModel.sendTextToDevice(device.id)
                    }
                }
            }
        )
    }
}