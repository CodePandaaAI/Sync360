package com.liftley.sync360.features.sync.domain.model

data class PickedFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

sealed interface SendItem {
    val id: String
    val displayName: String
    val mimeType: String
    val sizeBytes: Long

    data class File(val file: PickedFile) : SendItem {
        override val id: String = file.id
        override val displayName: String = file.name
        override val mimeType: String = file.mimeType
        override val sizeBytes: Long = file.sizeBytes
    }

    data class Text(
        override val id: String,
        val text: String,
        val preview: String
    ) : SendItem {
        override val displayName: String = "Text: $preview"
        override val mimeType: String = SYNC360_TEXT_MIME_TYPE
        override val sizeBytes: Long = text.encodeToByteArray().size.toLong()
    }
}

const val SYNC360_TEXT_MIME_TYPE = "application/x-sync360-text"

data class TransferFilePreview(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String? = null
)

data class ReceivedFileBatch(
    val senderName: String,
    val files: List<TransferFilePreview>,
    val savedPaths: List<String>,
    val senderDeviceId: String = ""
)

data class FileTransferProgress(
    val peerName: String,
    val files: List<TransferFilePreview>,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long? = null,
    val estimatedTimeRemainingSeconds: Long? = null,
    val direction: TransferDirection,
    val stage: TransferStage
) {
    val percent: Int get() = if (totalBytes > 0) ((bytesTransferred * 100.0) / totalBytes).toInt().coerceIn(0, 100) else 0
}

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
    RECEIVER_BUSY,
    NETWORK_FAILED,
    TIMED_OUT,
    WRITE_FAILED,
    SENDER_CANCELLED,
    RECEIVER_CANCELLED,
    INTERRUPTED,
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
