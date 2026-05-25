package com.liftley.sync360.features.sync.domain.model

data class DeviceStream(
    val deviceId: String,
    val clipboard: ClipboardEntry,
    val media: List<SyncAsset>,
    val documents: List<SyncAsset>,
    val storageUsedPercent: Int,
    val lastSeenLabel: String,
    val latestTexts: List<ClipboardEntry> = emptyList()
)


data class ClipboardEntry(
    val text: String,
    val updatedLabel: String,
    val sourceApp: String,
    val isFromMe: Boolean = false
)

data class SyncAsset(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: SyncAssetType,
    val syncState: SyncTransferState,
    val progressPercent: Int = 0,
    val path: String = "",
    val isFromMe: Boolean = false
)

enum class SyncAssetType {
    IMAGE,
    VIDEO,
    PDF,
    DOCUMENT,
    ARCHIVE
}

enum class SyncTransferState {
    THUMBNAIL_ONLY,
    DOWNLOADING,
    FULLY_DOWNLOADED
}
