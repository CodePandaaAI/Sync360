package com.liftley.sync360.features.sync.data.network

sealed interface HttpTransportResult {
    data class Success(val statusCode: Int) : HttpTransportResult
    data class Failure(
        val error: HttpTransportError,
        val statusCode: Int? = null,
        val detail: String? = null
    ) : HttpTransportResult

    val isSuccess: Boolean
        get() = this is Success
}

sealed interface FileOfferTransportResult {
    data class Accepted(
        val response: com.liftley.sync360.features.sync.data.network.api.FileOfferResponseDto
    ) : FileOfferTransportResult

    data class Failure(val transport: HttpTransportResult.Failure) : FileOfferTransportResult
}

enum class HttpTransportError {
    CLIENT_CLOSED,
    TIMEOUT,
    UNREACHABLE,
    BUSY,
    PROTOCOL_MISMATCH,
    REMOTE_STORAGE_FULL,
    REMOTE_STORAGE_UNAVAILABLE,
    INTEGRITY_FAILED,
    REJECTED,
    SOURCE_READ_FAILED,
    UNKNOWN
}
