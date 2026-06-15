package com.liftley.sync360.core.security

import java.security.MessageDigest

actual class Sha256Hasher actual constructor() {
    private val digest = MessageDigest.getInstance("SHA-256")

    actual fun update(bytes: ByteArray) {
        digest.update(bytes)
    }

    actual fun digestHex(): String =
        digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
