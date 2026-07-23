package com.liftley.sync360.data.network.http.dto.file

import kotlinx.serialization.Serializable

@Serializable
data class FileOfferRequest(
    val senderDeviceId: String,
    val senderDeviceName: String,
    val files: List<FileOfferItem>,
    val totalSizeBytes: Long
)