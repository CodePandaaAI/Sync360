package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable

@Serializable
data class TextTransferRequest(
    val text: String
)