package com.liftley.sync360.core.security

expect object SessionCrypto {
    fun secureToken(byteCount: Int = 32): String
    fun hmacSha256Hex(secret: String, message: String): String
}

