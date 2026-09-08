package com.liftley.sync360.presentation.send.model

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.domain.model.TextDeliveryLimits

data class SendScreenState(
    val fileReceiveCode: String,
    val selectedTab: SendTab = SendTab.Text,
    val textInput: String = "",
    val files: List<SelectedFile> = emptyList(),
    val fileReceiveCodePrompt: FileReceiveCodePrompt? = null,
    val sendState: SendState = SendState.Idle,
    val nearbyDevices: List<NearbyDeviceUiModel> = emptyList(),
    val isDiscoveryEnabled: Boolean = true,
    val discoveryErrorMessage: String? = null,
    val discoveryStatus: DiscoveryStatus = DiscoveryStatus.Idle,
    val registrationStatus: RegistrationStatus = RegistrationStatus.Idle
) {
    val isTextTooLong: Boolean
        get() = textInput.length > TextDeliveryLimits.MAX_CHARACTER_COUNT

    val isContentReadyToSend: Boolean
        get() = when (selectedTab) {
            SendTab.Text -> textInput.isNotBlank() && !isTextTooLong
            SendTab.Files -> files.isNotEmpty()
        }

    val deviceActionLabel: String
        get() = when (selectedTab) {
            SendTab.Text -> {
                when {
                    textInput.isBlank() -> "Enter text to send"
                    isTextTooLong -> "Text exceeds the character limit"
                    else -> "Click me to send text"
                }
            }

            SendTab.Files -> {
                if (files.isEmpty()) "Add files to send" else "Click me to send files"
            }
        }
}
