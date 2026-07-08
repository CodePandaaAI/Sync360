package com.liftley.sync360.data.network.http.dto.file

import kotlinx.serialization.Serializable

@Serializable
enum class FileOfferResponse {
    Accepted, Declined
}