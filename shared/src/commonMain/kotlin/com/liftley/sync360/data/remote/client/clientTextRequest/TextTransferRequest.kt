package com.liftley.sync360.data.remote.client.clientTextRequest

import kotlinx.serialization.Serializable

@Serializable
data class TextTransferRequest(
    val text: String
)