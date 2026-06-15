package com.liftley.sync360.features.sync.domain.model

sealed interface ConnectionEvent {
    data class WaitingForApproval(val deviceName: String) : ConnectionEvent
    data class Connected(val deviceName: String) : ConnectionEvent
    data class Failed(val message: String) : ConnectionEvent
}
