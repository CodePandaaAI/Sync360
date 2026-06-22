package com.liftley.sync360.core.security

expect class Sha256Hasher() {
    fun update(bytes: ByteArray)
    fun update(bytes: ByteArray, offset: Int, length: Int)
    fun digestHex(): String
}
