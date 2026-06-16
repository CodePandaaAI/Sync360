package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.features.sync.domain.model.PickedFile

sealed interface SyncEvent {
    object Disconnect : SyncEvent
    data class SendMessage(val text: String) : SyncEvent
    data class SwitchDevice(val deviceId: String) : SyncEvent
    data class AcceptConnection(val deviceId: String) : SyncEvent
    data class DeclineConnection(val deviceId: String) : SyncEvent
    object ToggleQuickSave : SyncEvent
    data class AcceptIncomingOffer(val offerId: String) : SyncEvent
    data class DeclineIncomingOffer(val offerId: String) : SyncEvent
    data class CopyClipboard(val deviceId: String) : SyncEvent
    data class UpdateOutgoingText(val text: String) : SyncEvent
    data class RequestConnect(val deviceId: String) : SyncEvent
    data class RequestConnectByHost(val hostAddress: String) : SyncEvent
    object ConfirmConnect : SyncEvent
    object DismissConnectRequest : SyncEvent
    object PasteFromClipboard : SyncEvent
    data class OpenFilePicker(val kind: FilePickerKind) : SyncEvent
    data class AddSelectedFiles(val files: List<PickedFile>) : SyncEvent
    object SendSelectedFiles : SyncEvent
    data class SendSelectedFilesTo(val deviceId: String) : SyncEvent
    data class SendTextTo(val deviceId: String) : SyncEvent
    data class SendDraftTo(val deviceId: String) : SyncEvent
    object ClearSelectedFiles : SyncEvent
    object DismissReceivedFiles : SyncEvent
    object DismissTransferFailure : SyncEvent
    object CancelTransfer : SyncEvent
    data class OpenFile(val path: String) : SyncEvent
    data class ShowFileInFolder(val path: String) : SyncEvent
    object OpenDownloadsFolder : SyncEvent
    object TriggerScan : SyncEvent
    object RestartSharing : SyncEvent
}
