package com.liftley.sync360.domain.model

data class SelectedFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?
)
