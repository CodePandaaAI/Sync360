package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TextTransferRequest(
    val operationId: Uuid,
    val senderDeviceId: String,
    val text: String
)
