package com.liftley.sync360.data.network.http.dto.text

import kotlinx.serialization.Serializable

@Serializable
enum class TextOfferResponse {
    Accepted, Declined
}