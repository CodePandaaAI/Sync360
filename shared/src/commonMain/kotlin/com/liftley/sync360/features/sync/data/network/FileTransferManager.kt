package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.core.platform.PlatformFileError
import com.liftley.sync360.core.security.SessionAuth
import com.liftley.sync360.core.security.Sha256Hasher
import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileTransferManager(
    private val platformOperations: PlatformOperations,
    private val httpClient: HttpSyncClient
) {
    private val activeIncomingWrites = mutableMapOf<String, IncomingFileWrite>()
    private val incomingFailures = mutableMapOf<String, IncomingUploadFailure>()
    
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

    suspend fun prepareOutgoingFiles(files: List<PickedFile>): List<PreparedOutgoingFile>? =
        withContext(Dispatchers.IO) {
            val prepared = mutableListOf<PreparedOutgoingFile>()
            for (file in files) {
                val hasher = Sha256Hasher()
                val read = platformOperations.readFileChunks(file, HASH_CHUNK_SIZE_BYTES) { chunk ->
                    hasher.update(chunk)
                }
                val bytesRead = (read as? FileOperationResult.Success<*>)?.value as? Long
                    ?: return@withContext null
                if (bytesRead != file.sizeBytes) return@withContext null
                prepared += PreparedOutgoingFile(file, hasher.digestHex())
            }
            prepared
        }

    fun initIncomingFileWrite(
        offerId: String,
        fileIndex: Int,
        fileName: String,
        expectedBytes: Long,
        expectedSha256: String
    ): Boolean {
        val key = "${offerId}_$fileIndex"
        val beginResult = platformOperations.beginFileWrite(fileName)
        val handle = (beginResult as? FileOperationResult.Success<*>)?.value as? String
        if (handle == null) {
            val error = (beginResult as? FileOperationResult.Failure)?.error
            synchronized(incomingFailures) {
                incomingFailures[key] = error.toIncomingUploadFailure()
            }
            return false
        }
        val registered = synchronized(activeIncomingWrites) {
            if (key in activeIncomingWrites) {
                false
            } else {
                activeIncomingWrites[key] = IncomingFileWrite(
                    handle = handle,
                    expectedBytes = expectedBytes,
                    expectedSha256 = expectedSha256
                )
                true
            }
        }
        if (!registered) {
            platformOperations.cancelFileWrite(handle)
            synchronized(incomingFailures) {
                incomingFailures[key] = IncomingUploadFailure.INVALID_REQUEST
            }
        }
        return registered
    }

    fun writeIncomingFileChunk(offerId: String, fileIndex: Int, chunk: ByteArray): Boolean {
        val key = "${offerId}_$fileIndex"
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites[key] } ?: return false
        if (write.bytesWritten + chunk.size > write.expectedBytes) {
            synchronized(incomingFailures) {
                incomingFailures[key] = IncomingUploadFailure.INTEGRITY
            }
            errorIncomingFileWrite(offerId, fileIndex)
            return false
        }

        val wrote = platformOperations.writeFileChunk(write.handle, chunk)
        if (wrote is FileOperationResult.Success) {
            write.bytesWritten += chunk.size
            write.hasher.update(chunk)
            currentIncomingWrittenBytes += chunk.size
            val percent = ((currentIncomingWrittenBytes.toDouble() / currentIncomingTotalBytes.toDouble()) * 99)
                .toInt()
                .coerceIn(1, 99)
            currentIncomingProgressCallback?.invoke(percent)
        }
        if (wrote is FileOperationResult.Failure) {
            synchronized(incomingFailures) {
                incomingFailures[key] = wrote.error.toIncomingUploadFailure()
            }
        }
        return wrote is FileOperationResult.Success
    }

    fun completeIncomingFileWrite(offerId: String, fileIndex: Int): String? {
        val key = "${offerId}_$fileIndex"
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites.remove(key) } ?: return null
        val receivedSha256 = write.hasher.digestHex()
        if (
            write.bytesWritten != write.expectedBytes ||
            !receivedSha256.equals(write.expectedSha256, ignoreCase = true)
        ) {
            synchronized(incomingFailures) {
                incomingFailures[key] = IncomingUploadFailure.INTEGRITY
            }
            platformOperations.cancelFileWrite(write.handle)
            return null
        }
        val finishResult = platformOperations.finishFileWrite(write.handle)
        val path = (finishResult as? FileOperationResult.Success<*>)?.value as? String
        if (path == null) {
            val error = (finishResult as? FileOperationResult.Failure)?.error
            synchronized(incomingFailures) {
                incomingFailures[key] = error.toIncomingUploadFailure()
            }
        }
        return path
    }

    fun errorIncomingFileWrite(offerId: String, fileIndex: Int) {
        val key = "${offerId}_$fileIndex"
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites.remove(key) }
        if (write != null) {
            platformOperations.cancelFileWrite(write.handle)
        }
    }

    fun consumeIncomingFailure(offerId: String, fileIndex: Int): IncomingUploadFailure? {
        val key = "${offerId}_$fileIndex"
        return synchronized(incomingFailures) { incomingFailures.remove(key) }
    }

    fun cancelAllIncomingWrites() {
        val writes = synchronized(activeIncomingWrites) {
            val active = activeIncomingWrites.values.toList()
            activeIncomingWrites.clear()
            active
        }
        writes.forEach { platformOperations.cancelFileWrite(it.handle) }
        currentIncomingTotalBytes = 0L
        currentIncomingWrittenBytes = 0L
        currentIncomingProgressCallback = null
        synchronized(incomingFailures) { incomingFailures.clear() }
    }

    suspend fun uploadOutgoingFiles(
        serverIp: String,
        serverPort: Int,
        offerId: String,
        files: List<PickedFile>,
        sessionToken: String,
        onProgress: (percent: Int) -> Unit
    ): HttpTransportResult = withContext(Dispatchers.IO) {
        val totalBytes = files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var bytesUploaded = 0L
        var failure: HttpTransportResult.Failure? = null

        files.forEachIndexed { index, file ->
            if (failure != null) return@forEachIndexed
            val authFields = SessionAuth.create(
                sessionToken = sessionToken,
                purpose = "file_upload",
                parts = listOf(offerId, index.toString())
            )
            
            val result = httpClient.uploadFileChunked(
                ip = serverIp,
                targetPort = serverPort,
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
            
            if (result is HttpTransportResult.Failure) {
                failure = result
            }
        }

        failure ?: HttpTransportResult.Success(200)
    }

    private companion object {
        const val HASH_CHUNK_SIZE_BYTES = 1024 * 1024
    }
}

enum class IncomingUploadFailure {
    INVALID_REQUEST,
    STORAGE_FULL,
    STORAGE_UNAVAILABLE,
    WRITE_FAILED,
    INTEGRITY
}

private fun PlatformFileError?.toIncomingUploadFailure(): IncomingUploadFailure = when (this) {
    PlatformFileError.STORAGE_FULL -> IncomingUploadFailure.STORAGE_FULL
    PlatformFileError.DESTINATION_UNAVAILABLE -> IncomingUploadFailure.STORAGE_UNAVAILABLE
    else -> IncomingUploadFailure.WRITE_FAILED
}

data class PreparedOutgoingFile(
    val file: PickedFile,
    val sha256: String
)

private data class IncomingFileWrite(
    val handle: String,
    val expectedBytes: Long,
    val expectedSha256: String,
    val hasher: Sha256Hasher = Sha256Hasher(),
    var bytesWritten: Long = 0L
)
