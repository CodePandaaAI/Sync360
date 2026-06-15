package com.liftley.sync360.features.sync.data.repository

internal class SessionTokenStore {
    private val tokens = mutableMapOf<String, String>()

    fun put(deviceId: String, token: String) = synchronized(tokens) {
        tokens[deviceId] = token
    }

    fun get(deviceId: String): String? = synchronized(tokens) { tokens[deviceId] }

    fun remove(deviceId: String): String? = synchronized(tokens) { tokens.remove(deviceId) }

    fun containsToken(token: String?): Boolean = synchronized(tokens) {
        token != null && tokens.values.any { it == token }
    }

    fun removeToken(token: String?) = synchronized(tokens) {
        if (token == null) return@synchronized
        tokens.filterValues { it == token }
            .keys
            .toList()
            .forEach(tokens::remove)
    }

    fun clear() = synchronized(tokens) {
        tokens.clear()
    }
}
