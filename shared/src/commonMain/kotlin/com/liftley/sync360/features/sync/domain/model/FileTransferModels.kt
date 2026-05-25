package com.liftley.sync360.features.sync.domain.model

data class PickedFile(
    val name: String,
    val mimeType: String,
    val content: ByteArray
) {
    val sizeBytes: Long get() = content.size.toLong()
}

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
