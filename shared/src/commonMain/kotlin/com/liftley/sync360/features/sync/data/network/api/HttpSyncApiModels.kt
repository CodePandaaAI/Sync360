package com.liftley.sync360.features.sync.data.network.api

import kotlinx.serialization.Serializable



@Serializable
data class FilePreviewDto(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val sha256: String
)

@Serializable
data class FileOfferDto(
    val offerId: String,
    val senderDeviceId: String,
    val senderName: String,
    val files: List<FilePreviewDto>
)

@Serializable
data class FileOfferResponseDto(
    val accepted: Boolean,
    val rawTcpHost: String? = null,
    val rawTcpPort: Int? = null,
    val transferId: String? = null,
    val transferToken: String? = null,
    val failureReason: String? = null
)

@Serializable
data class FileCompleteDto(
    val offerId: String,
    val senderDeviceId: String
)
