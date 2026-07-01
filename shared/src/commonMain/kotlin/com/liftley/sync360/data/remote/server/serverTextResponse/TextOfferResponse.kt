package com.liftley.sync360.data.remote.server.serverTextResponse

import kotlinx.serialization.Serializable

@Serializable
enum class TextOfferResponse {
    Accepted, Declined
}