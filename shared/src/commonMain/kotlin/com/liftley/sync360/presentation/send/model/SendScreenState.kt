package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.model.SelectedFile

data class SendScreenState(
    val selectedTab: SendTab = SendTab.Text,
    val textInput: String = "",
    val files: List<SelectedFile> = emptyList(),
    val sendOperationState: SendOperationState = SendOperationState.Idle,
    val nearbyDevices: List<NearbyDeviceUiModel> = emptyList(),
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle,
    val registrationStatus: RegistrationStatus = RegistrationStatus.Idle
) {
    val isContentReadyToSend: Boolean
        get() = when (selectedTab) {
            SendTab.Text -> textInput.isNotBlank()
            SendTab.Files -> files.isNotEmpty()
        }

    val deviceActionLabel: String
        get() = when (selectedTab) {
            SendTab.Text -> {
                if (textInput.isBlank()) "Enter text to send" else "Click me to send text"
            }
            SendTab.Files -> {
                if (files.isEmpty()) "Add files to send" else "Click me to send files"
            }
        }
}
