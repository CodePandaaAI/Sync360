package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.features.sync.domain.model.PickedFile

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
    data class AddSelectedFiles(val files: List<PickedFile>) : SyncEvent
    object SendSelectedFiles : SyncEvent
    object ClearSelectedFiles : SyncEvent
    data class AcceptFileOffer(val offerId: String) : SyncEvent
    data class DeclineFileOffer(val offerId: String) : SyncEvent
    object DismissReceivedFiles : SyncEvent
    object ClearUserMessage : SyncEvent
    data class OpenFile(val path: String) : SyncEvent
    object TriggerScan : SyncEvent
}
