package com.liftley.sync360.data.network.http.dto.file

import kotlinx.serialization.Serializable

@Serializable
data class FileOfferResponse(
    val status: FileOfferStatus
)

@Serializable
enum class FileOfferStatus {
    ACCEPTED,
    INVALID_CODE,
    RECEIVER_BUSY,
    PREPARATION_FAILED
}
