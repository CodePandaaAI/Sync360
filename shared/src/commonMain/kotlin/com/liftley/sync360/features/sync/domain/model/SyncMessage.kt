package com.liftley.sync360.features.sync.domain.model

data class SyncMessage(
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = 0L // Can be populated later if needed
)
