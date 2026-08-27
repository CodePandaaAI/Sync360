package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable

@Serializable
data class TextDeliveryResponse(
    val status: TextDeliveryStatus
)

@Serializable
enum class TextDeliveryStatus {
    DELIVERED,
    RECEIVER_BUSY,
    TEXT_TOO_LARGE
}
