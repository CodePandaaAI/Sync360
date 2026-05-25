package com.liftley.sync360.features.sync.domain.model

data class SyncMessage(
    val id: String,
    val peerDeviceId: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val isFile: Boolean = false,
    val fileName: String? = null
)
