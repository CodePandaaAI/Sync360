package com.liftley.sync360.features.sync.data.network.api

object SyncProtocol {
    const val VERSION = 2

    val capabilities = listOf(
        "text",
        "raw-file-v1",
        "sha256"
    )

    fun isCompatible(version: Int, peerCapabilities: List<String>): Boolean {
        return version == VERSION && peerCapabilities.containsAll(capabilities)
    }
}
