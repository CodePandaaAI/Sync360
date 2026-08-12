package com.liftley.sync360.data.network.http.dto.file

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class FileOfferRequest(
    val operationId: Uuid,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val offeredFiles: List<FileOfferItem>,
    val totalSizeBytes: Long
)