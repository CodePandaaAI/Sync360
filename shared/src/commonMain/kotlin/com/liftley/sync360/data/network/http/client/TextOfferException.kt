package com.liftley.sync360.data.network.http.client

class TextOfferException(response: String) : Exception("Offer status: $response")