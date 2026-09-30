package com.liftley.sync360.domain.model

enum class RegistrationStatus {
    Idle,
    Starting,
    FailedToStart,
    Running,
    Stopping,
    CleanupFailed
}
