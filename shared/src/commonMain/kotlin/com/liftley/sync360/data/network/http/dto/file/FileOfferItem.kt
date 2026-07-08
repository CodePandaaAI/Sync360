package com.liftley.sync360.data.network.http.dto.file

import kotlinx.serialization.Serializable

@Serializable
data class FileOfferItem(
    val fileName: String,
    val fileSizeBytes: Long?,
    val mimeType: String?
)