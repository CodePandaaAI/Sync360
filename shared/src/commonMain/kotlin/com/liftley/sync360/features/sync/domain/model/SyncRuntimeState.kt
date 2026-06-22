package com.liftley.sync360.features.sync.domain.model

sealed interface SyncRuntimeState {
    data object Stopped : SyncRuntimeState
    data object Starting : SyncRuntimeState
    data class Ready(val localAddresses: List<String>) : SyncRuntimeState
    data class Degraded(
        val localAddresses: List<String>,
        val reason: SyncRuntimeFailure
    ) : SyncRuntimeState
    data class Unavailable(val reason: SyncRuntimeFailure) : SyncRuntimeState
    data object Stopping : SyncRuntimeState
}

enum class SyncRuntimeFailure {
    NETWORK_UNAVAILABLE,
    SERVER_UNAVAILABLE,
    DISCOVERY_UNAVAILABLE,
    RUNTIME_CLOSED
}

enum class SyncStartResult {
    STARTED,
    ALREADY_RUNNING,
    NETWORK_UNAVAILABLE,
    SERVER_UNAVAILABLE,
    RUNTIME_CLOSED
}
