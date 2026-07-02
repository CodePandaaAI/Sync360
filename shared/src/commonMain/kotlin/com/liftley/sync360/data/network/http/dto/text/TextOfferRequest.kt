package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable

@Serializable
data class TextOfferRequest(
    val senderDeviceId: String,
    val senderDeviceName: String,
    val preview: String,
    val characterCount: Int
)