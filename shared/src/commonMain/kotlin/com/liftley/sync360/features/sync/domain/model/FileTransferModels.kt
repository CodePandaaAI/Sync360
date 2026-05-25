package com.liftley.sync360.features.sync.domain.model

data class PickedFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val content: ByteArray? = null
)

data class TransferFilePreview(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class IncomingFileOffer(
    val offerId: String,
    val senderDeviceId: String,
    val senderName: String,
    val files: List<TransferFilePreview>
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

enum class TransferDirection {
    SENDING,
    RECEIVING
}
