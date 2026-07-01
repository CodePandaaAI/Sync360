package com.liftley.sync360.data.remote.server.serverResponse

import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse(
    val success: Boolean = true,
    val message: String? = null
)
