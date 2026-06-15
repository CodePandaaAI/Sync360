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
    val sizeBytes: Long
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
    val direction: TransferDirection
)

data class FileTransferFailure(
    val peerName: String,
    val message: String,
    val failedFileName: String? = null,
    val direction: TransferDirection
)

enum class TransferDirection {
    SENDING,
    RECEIVING
}
