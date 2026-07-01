package com.liftley.sync360.data.remote.client.clientTextRequest

import kotlinx.serialization.Serializable

@Serializable
data class TextOfferRequest(
    val senderDeviceId: String,
    val senderDeviceName: String,
    val preview: String,
    val characterCount: Int
)