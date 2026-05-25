package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.debug.agentDebugLog
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.data.network.api.FilePreviewDto
import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileTransferManager(
    private val platformOperations: PlatformOperations,
    private val httpClient: HttpSyncClient
) {
    // Maps "offerId_fileIndex" to the platform file-write handle string
    private val activeIncomingHandles = mutableMapOf<String, String>()
    
    // Track total expected transfer size for receiver's progress bar
    private var currentIncomingTotalBytes = 0L
    private var currentIncomingWrittenBytes = 0L
    private var currentIncomingProgressCallback: ((percent: Int) -> Unit)? = null

    fun registerIncomingTotalSize(totalBytes: Long, onProgress: (percent: Int) -> Unit) {
        currentIncomingTotalBytes = totalBytes.coerceAtLeast(1L)
        currentIncomingWrittenBytes = 0L
        currentIncomingProgressCallback = onProgress
    }

    fun initIncomingFileWrite(offerId: String, fileIndex: Int, fileName: String) {
        val handle = platformOperations.beginFileWrite(fileName)
        if (handle != null) {
            val key = "${offerId}_$fileIndex"
            synchronized(activeIncomingHandles) {
                activeIncomingHandles[key] = handle
            }
        }
        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:initIncomingFileWrite",
            message = "init incoming write",
            hypothesisId = "B",
            data = mapOf("offerId" to offerId, "fileIndex" to fileIndex.toString(), "fileName" to fileName, "hasHandle" to (handle != null).toString())
        )
        // #endregion
    }

    fun writeIncomingFileChunk(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean {
        val key = "${offerId}_$fileIndex"
        val handle = synchronized(activeIncomingHandles) { activeIncomingHandles[key] } ?: return false
        val wrote = platformOperations.writeFileChunk(handle, chunk)
        if (wrote) {
            currentIncomingWrittenBytes += chunk.size
            val percent = ((currentIncomingWrittenBytes.toDouble() / currentIncomingTotalBytes.toDouble()) * 95).toInt().coerceIn(1, 95)
            currentIncomingProgressCallback?.invoke(percent)
        }
        return wrote
    }

    fun completeIncomingFileWrite(offerId: String, fileIndex: Int): String? {
        val key = "${offerId}_$fileIndex"
        val handle = synchronized(activeIncomingHandles) { activeIncomingHandles.remove(key) } ?: return null
        val savedPath = platformOperations.finishFileWrite(handle)
        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:completeIncomingFileWrite",
            message = "complete incoming write",
            hypothesisId = "C",
            data = mapOf("offerId" to offerId, "fileIndex" to fileIndex.toString(), "savedPath" to (savedPath ?: "null"))
        )
        // #endregion
        return savedPath
    }

    fun errorIncomingFileWrite(offerId: String, fileIndex: Int) {
        val key = "${offerId}_$fileIndex"
        val handle = synchronized(activeIncomingHandles) { activeIncomingHandles.remove(key) }
        if (handle != null) {
            platformOperations.cancelFileWrite(handle)
        }
        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:errorIncomingFileWrite",
            message = "error incoming write",
            hypothesisId = "E",
            data = mapOf("offerId" to offerId, "fileIndex" to fileIndex.toString())
        )
        // #endregion
    }

    suspend fun uploadOutgoingFiles(
        serverIp: String,
        offerId: String,
        files: List<PickedFile>,
        onProgress: (percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val totalBytes = files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var bytesUploaded = 0L
        var failed = false

        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:uploadOutgoingFiles",
            message = "upload batch start",
            hypothesisId = "A",
            data = mapOf("offerId" to offerId, "fileCount" to files.size.toString(), "totalBytes" to totalBytes.toString())
        )
        // #endregion

        files.forEachIndexed { index, file ->
            if (failed) return@forEachIndexed
            var lastFileUploadedBytes = 0L
            
            val success = httpClient.uploadFileChunked(
                ip = serverIp,
                offerId = offerId,
                fileIndex = index,
                file = file,
                platformOperations = platformOperations
            ) { bytesSent ->
                bytesUploaded += bytesSent
                val percent = ((bytesUploaded.toDouble() / totalBytes.toDouble()) * 95).toInt().coerceIn(1, 95)
                onProgress(percent)
            }
            
            if (!success) {
                // #region agent log
                agentDebugLog(
                    location = "FileTransferManager.kt:uploadOutgoingFiles",
                    message = "file upload failed in batch",
                    hypothesisId = "E",
                    data = mapOf("offerId" to offerId, "fileName" to file.name)
                )
                // #endregion
                failed = true
            }
        }

        // #region agent log
        agentDebugLog(
            location = "FileTransferManager.kt:uploadOutgoingFiles",
            message = "upload batch finished",
            hypothesisId = "C",
            data = mapOf("offerId" to offerId, "failed" to failed.toString())
        )
        // #endregion

        !failed
    }
}
