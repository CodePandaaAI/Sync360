package com.liftley.sync360.presentation.featureSend.model

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice

data class SendScreenState(
    val selectedTab: SendTab = SendTab.Text,
    val textInput: String = "",
    val textSendState: TextSendState = TextSendState.Idle,
    val nearbyDevices: List<NearbyDevice> = emptyList(),
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle
)