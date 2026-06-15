package com.liftley.sync360.features.sync.domain.model

object SyncProtocolLimits {
    const val MAX_CONTROL_BODY_BYTES = 256 * 1024L
    const val MAX_TEXT_LENGTH = 64 * 1024
    const val MAX_DEVICE_ID_LENGTH = 128
    const val MAX_DEVICE_NAME_LENGTH = 100
    const val MAX_DEVICE_TYPE_LENGTH = 32
    const val MAX_HOST_LENGTH = 253
    const val MAX_SESSION_TOKEN_LENGTH = 256
    const val MAX_NONCE_LENGTH = 128
    const val MAX_SIGNATURE_LENGTH = 128
    const val SESSION_TOKEN_HEX_LENGTH = 64
    const val NONCE_HEX_LENGTH = 32
    const val SIGNATURE_HEX_LENGTH = 64
    const val MAX_OFFER_ID_LENGTH = 128
    const val MAX_PENDING_CONNECT_REQUESTS = 8
    const val MAX_FILES_PER_TRANSFER = 12
    const val MAX_FILE_NAME_LENGTH = 255
    const val MAX_MIME_TYPE_LENGTH = 255
    const val MAX_FILE_BYTES = 10L * 1024 * 1024 * 1024
    const val MAX_BATCH_BYTES = 20L * 1024 * 1024 * 1024
    const val MIN_STORAGE_RESERVE_BYTES = 256L * 1024 * 1024
    const val MAX_CONNECT_REQUESTS_PER_MINUTE = 12
    const val MAX_CONTROL_REQUESTS_PER_MINUTE = 240
    const val MAX_FILE_UPLOADS_PER_MINUTE = 60
    const val SHA_256_HEX_LENGTH = 64
    const val FILE_IDLE_TIMEOUT_MILLIS = 120_000L
    const val MAX_AUTOMATIC_FILE_RETRIES = 0
}
