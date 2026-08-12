package com.liftley.sync360.data.network.http.dto

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CancelRequest(
    val operationId: Uuid,
    val senderDeviceId: String
)
