package com.liftley.sync360.data.remote.server.serverTextResponse

import kotlinx.serialization.Serializable

@Serializable
data class TextTransferResponse(
    val success: Boolean,
    val message: String? = null
)