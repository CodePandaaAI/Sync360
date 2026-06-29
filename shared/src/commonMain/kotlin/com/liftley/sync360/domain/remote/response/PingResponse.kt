package com.liftley.sync360.domain.remote.response

import kotlinx.serialization.Serializable

@Serializable
sealed interface PingRequestResponse {
    @Serializable
    data class Accepted(val response: PingResponse): PingRequestResponse

    @Serializable
    data class Declined(val reason: String): PingRequestResponse
}

@Serializable
data class PingResponse(
    val deviceUuid: String,
    val protocolVersion: String
)