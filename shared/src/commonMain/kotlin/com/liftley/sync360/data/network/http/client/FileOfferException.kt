package com.liftley.sync360.data.network.http.client

class FileOfferException(response: String) : Exception("Offer status: $response")