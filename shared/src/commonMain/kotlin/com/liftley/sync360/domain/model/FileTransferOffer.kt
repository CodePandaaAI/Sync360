package com.liftley.sync360.domain.model

data class FileTransferOffer(
    val senderDeviceId: String,
    val senderDeviceName: String,
    val files: List<OfferedFile>,
    val totalSizeBytes: Long
)

data class OfferedFile(
    val index: Int,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String?
)
