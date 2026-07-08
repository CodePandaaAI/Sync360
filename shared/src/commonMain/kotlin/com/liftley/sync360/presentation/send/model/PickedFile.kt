package com.liftley.sync360.presentation.send.model

data class PickedFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?
)