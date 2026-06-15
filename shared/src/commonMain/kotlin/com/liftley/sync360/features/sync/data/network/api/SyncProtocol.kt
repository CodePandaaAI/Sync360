package com.liftley.sync360.features.sync.data.network.api

object SyncProtocol {
    const val VERSION = 1

    val capabilities = listOf(
        "text",
        "file-stream",
        "sha256"
    )

    fun isCompatible(version: Int, peerCapabilities: List<String>): Boolean {
        return version == VERSION && peerCapabilities.containsAll(capabilities)
    }
}
