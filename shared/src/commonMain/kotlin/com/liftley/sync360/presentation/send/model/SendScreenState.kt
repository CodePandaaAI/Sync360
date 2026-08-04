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
    val selectedDeviceId: String? = null,
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle,
    val registrationStatus: RegistrationStatus = RegistrationStatus.Idle
) {
    val selectedDevice: NearbyDeviceUiModel?
        get() = nearbyDevices.firstOrNull { it.id == selectedDeviceId }

    val canSend: Boolean
        get() = selectedDevice != null && when (selectedTab) {
            SendTab.Text -> textInput.isNotBlank()
            SendTab.Files -> files.isNotEmpty()
        }

    val sendButtonLabel: String
        get() {
            val device = selectedDevice ?: return "Select a nearby device"
            val contentName = when (selectedTab) {
                SendTab.Text -> {
                    if (textInput.isBlank()) return "Enter text to send"
                    "text"
                }
                SendTab.Files -> {
                    val count = files.size
                    if (count == 0) return "Add files to send"
                    "$count ${if (count == 1) "file" else "files"}"
                }
            }
            return "Send $contentName to ${device.deviceName}"
        }
}
