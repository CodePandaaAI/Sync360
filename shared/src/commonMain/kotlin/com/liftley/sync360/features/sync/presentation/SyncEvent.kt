package com.liftley.sync360.features.sync.presentation

import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.features.sync.domain.model.PickedFile

sealed interface SyncEvent {
    object ToggleQuickSave : SyncEvent
    data class AcceptIncomingOffer(val offerId: String) : SyncEvent
    data class DeclineIncomingOffer(val offerId: String) : SyncEvent
    data class CopyClipboard(val text: String) : SyncEvent
    data class UpdateOutgoingText(val text: String) : SyncEvent
    object PasteFromClipboard : SyncEvent
    data class OpenFilePicker(val kind: FilePickerKind) : SyncEvent
    data class AddSelectedFiles(val files: List<PickedFile>) : SyncEvent
    data class AddCustomText(val text: String) : SyncEvent

    data class SendSelectedItemsToHost(val hostAddress: String) : SyncEvent
    data class SendSelectedItemsTo(val deviceId: String) : SyncEvent
    data class ProposeSendTo(val deviceId: String) : SyncEvent
    object CancelSendProposal : SyncEvent
    object ClearSelectedItems : SyncEvent
    data class RemoveSelectedItem(val itemId: String) : SyncEvent
    object DismissReceivedFiles : SyncEvent
    object DismissTransferFailure : SyncEvent
    object CancelTransfer : SyncEvent
    data class OpenFile(val path: String) : SyncEvent
    data class ShowFileInFolder(val path: String) : SyncEvent
    object OpenDownloadsFolder : SyncEvent
    object TriggerScan : SyncEvent
    object RestartSharing : SyncEvent
}
