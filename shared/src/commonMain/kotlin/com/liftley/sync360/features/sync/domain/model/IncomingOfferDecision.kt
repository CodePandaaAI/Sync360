package com.liftley.sync360.features.sync.domain.model

sealed interface PendingIncomingOffer {
    val offerId: String
    val senderDeviceId: String
    val senderName: String

    data class Files(
        override val offerId: String,
        override val senderDeviceId: String,
        override val senderName: String,
        val fileCount: Int,
        val totalBytes: Long,
        val files: List<TransferFilePreview>
    ) : PendingIncomingOffer

    data class Text(
        override val offerId: String,
        override val senderDeviceId: String,
        override val senderName: String,
        val preview: String
    ) : PendingIncomingOffer
}
