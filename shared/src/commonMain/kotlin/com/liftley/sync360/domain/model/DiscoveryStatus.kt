package com.liftley.sync360.domain.model

enum class DiscoveryStatus {
    Idle,
    Starting,
    Running,
    Stopping,
    /** Cleanup failed; resources are still owned and must be stopped before reuse. */
    CleanupFailed
}