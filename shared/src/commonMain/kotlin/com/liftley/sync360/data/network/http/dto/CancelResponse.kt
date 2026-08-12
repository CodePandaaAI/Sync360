package com.liftley.sync360.data.network.http.dto

import kotlinx.serialization.Serializable

@Serializable
data class CancelResponse(
    val cancelled: Boolean
)
