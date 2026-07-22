package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.domain.model.FileTransferProgress

internal class FileTransferProgressTracker(
    private val totalBytes: Long,
    private val onProgress: (FileTransferProgress) -> Unit
) {
    private var bytesTransferred = 0L
    private var lastPublishedPercentage = -1

    fun addBytes(byteCount: Int) {
        bytesTransferred += byteCount

        val progress = FileTransferProgress(
            bytesTransferred = bytesTransferred.coerceAtMost(totalBytes),
            totalBytes = totalBytes
        )
        val transferFinished = bytesTransferred >= totalBytes

        if (!transferFinished && progress.percentage == lastPublishedPercentage) {
            return
        }

        lastPublishedPercentage = progress.percentage
        onProgress(progress)
    }
}
