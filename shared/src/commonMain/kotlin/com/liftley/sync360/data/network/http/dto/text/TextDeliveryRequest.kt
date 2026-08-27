package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable

@Serializable
data class TextDeliveryRequest(
    val senderDeviceName: String,
    val text: String
)
