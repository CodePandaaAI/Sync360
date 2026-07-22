package com.liftley.sync360.domain.model

data class FileTransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) {
            0f
        } else {
            (bytesTransferred.toDouble() / totalBytes.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
        }

    val percentage: Int
        get() = (fraction * 100).toInt().coerceIn(0, 100)

    companion object {
        fun waiting(totalBytes: Long) = FileTransferProgress(
            bytesTransferred = 0L,
            totalBytes = totalBytes
        )
    }
}
