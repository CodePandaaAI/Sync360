package com.liftley.sync360.features.sync.data.network.api

internal object HttpSyncRoutes {
    const val SessionTokenHeader = "X-Sync360-Session-Token"
    const val IssuedAtHeader = "X-Sync360-Issued-At"
    const val NonceHeader = "X-Sync360-Nonce"
    const val SignatureHeader = "X-Sync360-Signature"

    const val ConnectRequest = "/api/connect/request"
    const val ConnectAccept = "/api/connect/accept"
    const val ConnectReject = "/api/connect/reject"
    const val TextMessage = "/api/message/text"
    const val FileOffer = "/api/file/offer"
    const val FileComplete = "/api/file/complete"
}
