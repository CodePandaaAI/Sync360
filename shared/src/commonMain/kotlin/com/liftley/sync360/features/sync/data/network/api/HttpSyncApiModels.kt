package com.liftley.sync360.features.sync.data.network.api

import kotlinx.serialization.Serializable

@Serializable
data class ConnectRequestDto(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val senderIp: String
)

@Serializable
data class ConnectAcceptDto(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val senderIp: String
)

@Serializable
data class MessageDto(
    val messageId: String,
    val senderDeviceId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long
)

@Serializable
data class FilePreviewDto(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long
)

@Serializable
data class FileOfferDto(
    val offerId: String,
    val senderDeviceId: String,
    val senderName: String,
    val files: List<FilePreviewDto>
)

@Serializable
data class FileAcceptDto(
    val offerId: String,
    val senderDeviceId: String
)

@Serializable
data class FileRejectDto(
    val offerId: String,
    val senderDeviceId: String
)

@Serializable
data class FileCompleteDto(
    val offerId: String,
    val senderDeviceId: String
)
