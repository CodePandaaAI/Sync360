package com.liftley.sync360.presentation.featureSend.model

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.presentation.model.NearbyDeviceUiModel

data class SendScreenState(
    val selectedTab: SendTab = SendTab.Text,
    val textInput: String = "",
    val textSendState: TextSendState = TextSendState.Idle,
    val nearbyDevices: List<NearbyDeviceUiModel> = emptyList(),
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle
)