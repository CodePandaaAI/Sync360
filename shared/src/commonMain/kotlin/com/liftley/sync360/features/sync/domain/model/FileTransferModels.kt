package com.liftley.sync360.features.sync.domain.model

data class PickedFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class TransferFilePreview(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String? = null
)

data class ReceivedFileBatch(
    val senderName: String,
    val files: List<TransferFilePreview>,
    val savedPaths: List<String>
)

data class FileTransferProgress(
    val peerName: String,
    val files: List<TransferFilePreview>,
    val percent: Int,
    val direction: TransferDirection,
    val stage: TransferStage
)

data class FileTransferFailure(
    val peerName: String,
    val message: String,
    val failedFileName: String? = null,
    val direction: TransferDirection,
    val reason: TransferFailureReason = TransferFailureReason.UNKNOWN
)

enum class TransferFailureReason {
    INVALID_SELECTION,
    SOURCE_UNAVAILABLE,
    STORAGE_FULL,
    STORAGE_UNAVAILABLE,
    INTEGRITY_FAILED,
    RECEIVER_UNAVAILABLE,
    NETWORK_FAILED,
    TIMED_OUT,
    WRITE_FAILED,
    UNKNOWN
}

enum class TransferDirection {
    SENDING,
    RECEIVING
}

enum class TransferStage {
    PREPARING,
    TRANSFERRING,
    VERIFYING
}
