package com.liftley.sync360.features.sync.data.network.api

import kotlinx.serialization.Serializable

@Serializable
data class ConnectRequestDto(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val senderIp: String,
    val senderPort: Int = 8080,
    val sessionToken: String,
    val issuedAtMillis: Long,
    val nonce: String,
    val signature: String,
    val protocolVersion: Int = SyncProtocol.VERSION,
    val capabilities: List<String> = emptyList(),
    val publicKey: String? = null
)

@Serializable
data class ConnectAcceptDto(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val senderIp: String,
    val senderPort: Int = 8080,
    val sessionToken: String,
    val issuedAtMillis: Long,
    val nonce: String,
    val signature: String,
    val protocolVersion: Int = SyncProtocol.VERSION,
    val capabilities: List<String> = emptyList(),
    val publicKey: String? = null
)

@Serializable
data class ConnectRejectDto(
    val senderDeviceId: String,
    val sessionToken: String? = null,
    val issuedAtMillis: Long = 0L,
    val nonce: String = "",
    val signature: String = ""
)

@Serializable
data class MessageDto(
    val messageId: String,
    val senderDeviceId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val sessionToken: String,
    val issuedAtMillis: Long,
    val nonce: String,
    val signature: String
)

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
    val files: List<FilePreviewDto>,
    val sessionToken: String,
    val issuedAtMillis: Long,
    val nonce: String,
    val signature: String
)

@Serializable
data class FileCompleteDto(
    val offerId: String,
    val senderDeviceId: String,
    val sessionToken: String,
    val issuedAtMillis: Long,
    val nonce: String,
    val signature: String
)
