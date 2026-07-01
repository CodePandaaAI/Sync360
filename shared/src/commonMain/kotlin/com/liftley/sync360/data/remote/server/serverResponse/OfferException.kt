package com.liftley.sync360.data.remote.server.serverResponse

class OfferException(response: OfferResponse) : Exception("Offer status: $response")