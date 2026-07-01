package com.liftley.sync360.data.remote.client.clientRequest

import kotlinx.serialization.Serializable


@Serializable
data class MessagePayload(
    val content: String
)
