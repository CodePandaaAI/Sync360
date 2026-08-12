package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class TextOfferRequest(
    val operationId: Uuid,
    val senderDeviceId: String,
    val senderDeviceName: String,
    val preview: String,
    val characterCount: Int
)