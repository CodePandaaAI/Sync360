package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.SelectedFile

data class SendScreenState(
    val selectedTab: SendTab = SendTab.Text,
    val textInput: String = "",
    val files: List<SelectedFile> = emptyList(),
    val sendOperationState: SendOperationState = SendOperationState.Idle,
    val nearbyDevices: List<NearbyDeviceUiModel> = emptyList(),
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle
)
