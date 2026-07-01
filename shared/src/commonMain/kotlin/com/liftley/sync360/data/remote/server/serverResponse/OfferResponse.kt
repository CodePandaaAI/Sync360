package com.liftley.sync360.data.remote.server.serverResponse

import kotlinx.serialization.Serializable

@Serializable
enum class OfferResponse {
    OfferAccepted, OfferDeclined
}