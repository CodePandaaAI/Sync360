package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.SessionAuth
import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileTransferManager(
    private val platformOperations: PlatformOperations,
    private val httpClient: HttpSyncClient
) {
    // Maps "offerId_fileIndex" to the platform file-write handle string.
    private val activeIncomingHandles = mutableMapOf<String, String>()
    
    // Receiver progress means "bytes written into the platform file sink".
    // It is capped below 100 until the platform finalizes the file.
    private var currentIncomingTotalBytes = 0L
    private var currentIncomingWrittenBytes = 0L
    private var currentIncomingProgressCallback: ((percent: Int) -> Unit)? = null

    fun registerIncomingTotalSize(totalBytes: Long, onProgress: (percent: Int) -> Unit) {
        currentIncomingTotalBytes = totalBytes.coerceAtLeast(1L)
        currentIncomingWrittenBytes = 0L
        currentIncomingProgressCallback = onProgress
    }

    fun initIncomingFileWrite(offerId: String, fileIndex: Int, fileName: String): Boolean {
        val handle = platformOperations.beginFileWrite(fileName) ?: return false
        val key = "${offerId}_$fileIndex"
        synchronized(activeIncomingHandles) {
            activeIncomingHandles[key] = handle
        }
        return true
    }

    fun writeIncomingFileChunk(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean {
        val key = "${offerId}_$fileIndex"
        val handle = synchronized(activeIncomingHandles) { activeIncomingHandles[key] } ?: return false
        val wrote = platformOperations.writeFileChunk(handle, chunk)
        if (wrote) {
            currentIncomingWrittenBytes += chunk.size
            val percent = ((currentIncomingWrittenBytes.toDouble() / currentIncomingTotalBytes.toDouble()) * 99)
                .toInt()
                .coerceIn(1, 99)
            currentIncomingProgressCallback?.invoke(percent)
        }
        return wrote
    }

    fun completeIncomingFileWrite(offerId: String, fileIndex: Int): String? {
        val key = "${offerId}_$fileIndex"
        val handle = synchronized(activeIncomingHandles) { activeIncomingHandles.remove(key) } ?: return null
        return platformOperations.finishFileWrite(handle)
    }

    fun errorIncomingFileWrite(offerId: String, fileIndex: Int) {
        val key = "${offerId}_$fileIndex"
        val handle = synchronized(activeIncomingHandles) { activeIncomingHandles.remove(key) }
        if (handle != null) {
            platformOperations.cancelFileWrite(handle)
        }
    }

    suspend fun uploadOutgoingFiles(
        serverIp: String,
        offerId: String,
        files: List<PickedFile>,
        sessionToken: String,
        onProgress: (percent: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val totalBytes = files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var bytesUploaded = 0L
        var failed = false

        files.forEachIndexed { index, file ->
            if (failed) return@forEachIndexed
            val authFields = SessionAuth.create(
                sessionToken = sessionToken,
                purpose = "file_upload",
                parts = listOf(offerId, index.toString())
            )
            
            val success = httpClient.uploadFileChunked(
                ip = serverIp,
                offerId = offerId,
                fileIndex = index,
                file = file,
                sessionToken = sessionToken,
                authFields = authFields,
                platformOperations = platformOperations
            ) { bytesSent ->
                bytesUploaded += bytesSent
                val percent = ((bytesUploaded.toDouble() / totalBytes.toDouble()) * 95).toInt().coerceIn(1, 95)
                onProgress(percent)
            }
            
            if (!success) {
                failed = true
            }
        }

        !failed
    }
}
