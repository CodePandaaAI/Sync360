package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.features.sync.domain.model.DeviceProfile

sealed interface SyncEvent {
    data class OnIpChange(val ip: String) : SyncEvent
    object Connect : SyncEvent
    object Disconnect : SyncEvent
    data class SendMessage(val text: String) : SyncEvent
    object SendCurrentClipboard : SyncEvent
    data class ReceiveMessage(val text: String, val isFromMe: Boolean) : SyncEvent
    data class SwitchDevice(val deviceId: String) : SyncEvent
    data class PairWithDevice(val deviceId: String) : SyncEvent
    data class AcceptPairing(val deviceId: String) : SyncEvent
    data class DeclinePairing(val deviceId: String) : SyncEvent
    data class CopyClipboard(val deviceId: String) : SyncEvent
    data class RequestDownload(val assetId: String) : SyncEvent
    data class SetOverlayEnabled(val enabled: Boolean) : SyncEvent
    data class SetBackgroundMonitoringEnabled(val enabled: Boolean) : SyncEvent
    data class UpdateOutgoingText(val text: String) : SyncEvent
    data class RequestConnect(val deviceId: String) : SyncEvent
    object ConfirmConnect : SyncEvent
    object DismissConnectRequest : SyncEvent
    object PasteFromClipboard : SyncEvent
    data class OpenFilePicker(val mimeType: String) : SyncEvent
    data class SendFile(val name: String, val mimeType: String, val content: ByteArray) : SyncEvent
    object AcceptFileOffer : SyncEvent
    object DeclineFileOffer : SyncEvent
}

