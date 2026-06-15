package com.liftley.sync360.core.security

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class SessionAuthFields(
    val issuedAtMillis: Long,
    val nonce: String,
    val signature: String
)

@OptIn(ExperimentalTime::class)
object SessionAuth {
    private const val MAX_CLOCK_SKEW_MILLIS = 5 * 60 * 1000L

    fun create(sessionToken: String, purpose: String, parts: List<String>): SessionAuthFields {
        val issuedAtMillis = Clock.System.now().toEpochMilliseconds()
        val nonce = SessionCrypto.secureToken(16)
        return SessionAuthFields(
            issuedAtMillis = issuedAtMillis,
            nonce = nonce,
            signature = sign(sessionToken, purpose, issuedAtMillis, nonce, parts)
        )
    }

    fun verify(
        fields: SessionAuthFields,
        sessionToken: String,
        purpose: String,
        parts: List<String>,
        replayCache: SessionReplayCache
    ): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        if (fields.issuedAtMillis !in (now - MAX_CLOCK_SKEW_MILLIS)..(now + MAX_CLOCK_SKEW_MILLIS)) {
            return false
        }
        val expected = sign(sessionToken, purpose, fields.issuedAtMillis, fields.nonce, parts)
        if (!constantTimeEquals(expected, fields.signature)) return false

        return replayCache.markIfNew(sessionToken, fields.nonce)
    }

    private fun sign(
        sessionToken: String,
        purpose: String,
        issuedAtMillis: Long,
        nonce: String,
        parts: List<String>
    ): String {
        val message = buildString {
            append(purpose)
            append('\n')
            append(issuedAtMillis)
            append('\n')
            append(nonce)
            parts.forEach { part ->
                append('\n')
                append(part)
            }
        }
        return SessionCrypto.hmacSha256Hex(sessionToken, message)
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var result = 0
        for (index in left.indices) {
            result = result or (left[index].code xor right[index].code)
        }
        return result == 0
    }
}

class SessionReplayCache {
    private val seen = ArrayDeque<String>()
    private val seenSet = mutableSetOf<String>()

    fun markIfNew(sessionToken: String, nonce: String): Boolean {
        val key = "$sessionToken:$nonce"
        if (!seenSet.add(key)) return false

        seen.addLast(key)
        while (seen.size > MAX_ENTRIES) {
            seenSet.remove(seen.removeFirst())
        }
        return true
    }

    fun clear() {
        seen.clear()
        seenSet.clear()
    }

    private companion object {
        const val MAX_ENTRIES = 512
    }
}
