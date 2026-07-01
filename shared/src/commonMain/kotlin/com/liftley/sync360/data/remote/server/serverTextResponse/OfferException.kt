package com.liftley.sync360.data.remote.server.serverTextResponse

class OfferException(response: String) : Exception("Offer status: $response")