package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.core.platform.FilePickerKind

sealed interface SyncEvent {
    object Disconnect : SyncEvent
    data class SendMessage(val text: String) : SyncEvent
    data class SwitchDevice(val deviceId: String) : SyncEvent
    data class AcceptPairing(val deviceId: String) : SyncEvent
    data class DeclinePairing(val deviceId: String) : SyncEvent
    data class CopyClipboard(val deviceId: String) : SyncEvent
    data class UpdateOutgoingText(val text: String) : SyncEvent
    data class RequestConnect(val deviceId: String) : SyncEvent
    object ConfirmConnect : SyncEvent
    object DismissConnectRequest : SyncEvent
    object PasteFromClipboard : SyncEvent
    data class OpenFilePicker(val kind: FilePickerKind) : SyncEvent
    data class SendFile(val name: String, val mimeType: String, val content: ByteArray) : SyncEvent
    object ClearUserMessage : SyncEvent
    data class OpenFile(val path: String) : SyncEvent
    object TriggerScan : SyncEvent
}
