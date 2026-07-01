package com.liftley.sync360.data.remote.client.clientRequest

import kotlinx.serialization.Serializable

@Serializable
data class OfferRequest(
    val deviceName: String,
    val deviceIp: String,
    val devicePort: Int,
    val deviceId: String,
    val filesCount: Int,
)