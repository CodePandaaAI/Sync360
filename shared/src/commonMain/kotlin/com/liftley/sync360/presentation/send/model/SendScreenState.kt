package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.DiscoveryStatus

data class SendScreenState(
    val selectedTab: SendTab = SendTab.Text,
    val textInput: String = "",
    val textSendState: TextSendState = TextSendState.Idle,
    val files: List<PickedFile> = emptyList(),
    val nearbyDevices: List<NearbyDeviceUiModel> = emptyList(),
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle
)