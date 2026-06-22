package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.core.platform.PlatformFileError
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.security.Sha256Hasher
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.SYNC360_TEXT_MIME_TYPE
import com.liftley.sync360.features.sync.domain.model.SendItem
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalEncodingApi::class)
class TransferPayloadStore(
    private val platformOperations: PlatformOperations
) {
    private val activeIncomingWrites = mutableMapOf<String, IncomingFileWrite>()
    private val activeTextWrites = mutableMapOf<String, MutableList<ByteArray>>()
    private val incomingFailures = mutableMapOf<String, IncomingUploadFailure>()
    private var currentIncomingWrittenBytes = 0L
    private var currentIncomingProgressCallback: ((bytes: Long) -> Unit)? = null

    fun registerIncomingTotalSize(totalBytes: Long, onProgress: (bytes: Long) -> Unit) {
        val cappedTotalBytes = totalBytes.coerceAtLeast(1L)
        currentIncomingWrittenBytes = 0L
        currentIncomingProgressCallback = { bytes ->
            onProgress(bytes.coerceAtMost(cappedTotalBytes))
        }
    }

    suspend fun prepareOutgoingItems(items: List<SendItem>): List<PreparedOutgoingFile>? =
        withContext(Dispatchers.IO) {
            val prepared = mutableListOf<PreparedOutgoingFile>()
            for (item in items) {
                val hasher = Sha256Hasher()
                val readResult = if (item is SendItem.Text) {
                    val bytes = item.text.encodeToByteArray()
                    hasher.update(bytes, 0, bytes.size)
                    FileOperationResult.Success(bytes.size.toLong())
                } else {
                    platformOperations.readFileChunks(
                        file = (item as SendItem.File).file,
                        chunkSizeBytes = HASH_CHUNK_SIZE_BYTES
                    ) { bytes, offset, length ->
                        hasher.update(bytes, offset, length)
                    }
                }
                val bytesRead = (readResult as? FileOperationResult.Success<*>)?.value as? Long
                if (bytesRead != item.sizeBytes) return@withContext null
                prepared += PreparedOutgoingFile(item, hasher.digestHex())
            }
            prepared
        }

    fun initIncomingFileWrite(
        offerId: String,
        fileIndex: Int,
        fileName: String,
        mimeType: String,
        expectedBytes: Long,
        expectedSha256: String,
        dispatcher: String = "Raw TCP receiver coroutine"
    ): Boolean {
        val key = incomingKey(offerId, fileIndex)
        if (key in synchronized(activeIncomingWrites) { activeIncomingWrites.keys }) {
            recordIncomingFailure(key, IncomingUploadFailure.INVALID_REQUEST)
            return false
        }

        if (mimeType == SYNC360_TEXT_MIME_TYPE) {
            synchronized(activeTextWrites) { activeTextWrites[key] = mutableListOf() }
            synchronized(activeIncomingWrites) {
                activeIncomingWrites[key] = IncomingFileWrite(
                    handle = textHandle(key),
                    expectedBytes = expectedBytes,
                    expectedSha256 = expectedSha256
                )
            }
            return true
        }

        val beginResult = platformOperations.beginFileWrite(fileName)
        val handle = (beginResult as? FileOperationResult.Success<*>)?.value as? String
        if (handle == null) {
            val error = (beginResult as? FileOperationResult.Failure)?.error
            recordIncomingFailure(key, error.toIncomingUploadFailure())
            return false
        }

        synchronized(activeIncomingWrites) {
            activeIncomingWrites[key] = IncomingFileWrite(
                handle = handle,
                expectedBytes = expectedBytes,
                expectedSha256 = expectedSha256
            )
        }
        return true
    }

    fun writeIncomingFileChunk(
        offerId: String,
        fileIndex: Int,
        chunk: ByteArray,
        offset: Int,
        length: Int
    ): Boolean {
        val key = incomingKey(offerId, fileIndex)
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites[key] } ?: return false
        if (write.bytesWritten + length > write.expectedBytes) {
            recordIncomingFailure(key, IncomingUploadFailure.INTEGRITY)
            errorIncomingFileWrite(offerId, fileIndex)
            return false
        }

        if (write.handle.startsWith(TEXT_HANDLE_PREFIX)) {
            val chunks = synchronized(activeTextWrites) { activeTextWrites[key] } ?: return false
            chunks += chunk.copyOfRange(offset, offset + length)
            write.acceptBytes(chunk, offset, length)
            publishIncomingProgress(length)
            return true
        }

        val result = platformOperations.writeFileChunk(write.handle, chunk, offset, length)
        if (result is FileOperationResult.Failure) {
            recordIncomingFailure(key, result.error.toIncomingUploadFailure())
            return false
        }
        write.acceptBytes(chunk, offset, length)
        publishIncomingProgress(length)
        return true
    }

    fun completeIncomingFileWrite(offerId: String, fileIndex: Int): String? {
        val key = incomingKey(offerId, fileIndex)
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites.remove(key) } ?: return null
        return if (write.handle.startsWith(TEXT_HANDLE_PREFIX)) {
            completeIncomingText(key, write)
        } else {
            completeIncomingFile(key, write)
        }
    }

    fun errorIncomingFileWrite(offerId: String, fileIndex: Int) {
        val key = incomingKey(offerId, fileIndex)
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites.remove(key) } ?: return
        if (write.handle.startsWith(TEXT_HANDLE_PREFIX)) {
            synchronized(activeTextWrites) { activeTextWrites.remove(key) }
        } else {
            platformOperations.cancelFileWrite(write.handle)
        }
    }

    fun consumeIncomingFailure(offerId: String, fileIndex: Int): IncomingUploadFailure? {
        return synchronized(incomingFailures) { incomingFailures.remove(incomingKey(offerId, fileIndex)) }
    }

    fun cancelAllIncomingWrites() {
        val writes = synchronized(activeIncomingWrites) {
            activeIncomingWrites.values.toList().also { activeIncomingWrites.clear() }
        }
        writes.forEach { write ->
            if (!write.handle.startsWith(TEXT_HANDLE_PREFIX)) {
                platformOperations.cancelFileWrite(write.handle)
            }
        }
        synchronized(activeTextWrites) { activeTextWrites.clear() }
        synchronized(incomingFailures) { incomingFailures.clear() }
        currentIncomingWrittenBytes = 0L
        currentIncomingProgressCallback = null
    }

    fun deleteFiles(paths: List<String>) {
        paths.forEach(platformOperations::deleteFile)
    }

    suspend fun uploadOutgoingFilesRaw(
        rawTransport: RawFileByteSender,
        serverIp: String,
        serverPort: Int,
        offerId: String,
        transferToken: String,
        files: List<PickedFile>,
        onProgress: (bytes: Long) -> Unit
    ): HttpTransportResult = uploadOutgoingItemsRaw(
        rawTransport = rawTransport,
        serverIp = serverIp,
        serverPort = serverPort,
        offerId = offerId,
        transferToken = transferToken,
        items = files.map { SendItem.File(it) },
        onProgress = onProgress
    )

    suspend fun uploadOutgoingItemsRaw(
        rawTransport: RawFileByteSender,
        serverIp: String,
        serverPort: Int,
        offerId: String,
        transferToken: String,
        items: List<SendItem>,
        onProgress: (bytes: Long) -> Unit
    ): HttpTransportResult = withContext(Dispatchers.IO) {
        var bytesUploaded = 0L
        var result: HttpTransportResult = HttpTransportResult.Success(200)
        items.forEachIndexed { index, item ->
            if (result is HttpTransportResult.Failure) return@forEachIndexed
            val rawResult = rawTransport.send(
                host = serverIp,
                port = serverPort,
                header = RawTcpFileHeader(
                    transferToken = transferToken,
                    transferId = offerId,
                    fileIndex = index,
                    contentLength = item.sizeBytes,
                    fileIdentifier = item.displayName
                ),
                item = item,
                platformOperations = platformOperations
            ) { bytesSent ->
                bytesUploaded += bytesSent
                onProgress(bytesUploaded)
            }
            result = when (rawResult) {
                is RawTcpSendResult.Success -> HttpTransportResult.Success(200)
                is RawTcpSendResult.Failure -> rawResult.toHttpTransportFailure()
            }
        }
        result
    }

    private fun completeIncomingText(key: String, write: IncomingFileWrite): String? {
        val chunks = synchronized(activeTextWrites) { activeTextWrites.remove(key) } ?: return null
        if (!write.hasExpectedHash()) {
            recordIncomingFailure(key, IncomingUploadFailure.INTEGRITY)
            return null
        }
        val combined = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }
        return "text_content:${Base64.encode(combined)}"
    }

    private fun completeIncomingFile(key: String, write: IncomingFileWrite): String? {
        if (!write.hasExpectedHash()) {
            recordIncomingFailure(key, IncomingUploadFailure.INTEGRITY)
            platformOperations.cancelFileWrite(write.handle)
            return null
        }
        val finishResult = platformOperations.finishFileWrite(write.handle)
        val path = (finishResult as? FileOperationResult.Success<*>)?.value as? String
        if (path == null) {
            val error = (finishResult as? FileOperationResult.Failure)?.error
            recordIncomingFailure(key, error.toIncomingUploadFailure())
        }
        return path
    }

    private fun publishIncomingProgress(bytesWritten: Int) {
        currentIncomingWrittenBytes += bytesWritten
        currentIncomingProgressCallback?.invoke(currentIncomingWrittenBytes)
    }

    private fun recordIncomingFailure(key: String, failure: IncomingUploadFailure) {
        synchronized(incomingFailures) { incomingFailures[key] = failure }
    }

    private fun incomingKey(offerId: String, fileIndex: Int): String = "${offerId}_$fileIndex"

    private fun textHandle(key: String): String = "$TEXT_HANDLE_PREFIX$key"

    private companion object {
        const val HASH_CHUNK_SIZE_BYTES = 1024 * 1024
        const val TEXT_HANDLE_PREFIX = "text_handle_"
    }
}

enum class IncomingUploadFailure {
    INVALID_REQUEST,
    STORAGE_FULL,
    STORAGE_UNAVAILABLE,
    WRITE_FAILED,
    INTEGRITY,
    INTERRUPTED
}

private fun PlatformFileError?.toIncomingUploadFailure(): IncomingUploadFailure = when (this) {
    PlatformFileError.STORAGE_FULL -> IncomingUploadFailure.STORAGE_FULL
    PlatformFileError.DESTINATION_UNAVAILABLE -> IncomingUploadFailure.STORAGE_UNAVAILABLE
    else -> IncomingUploadFailure.WRITE_FAILED
}

data class PreparedOutgoingFile(
    val item: SendItem,
    val sha256: String
)

private data class IncomingFileWrite(
    val handle: String,
    val expectedBytes: Long,
    val expectedSha256: String,
    val hasher: Sha256Hasher = Sha256Hasher(),
    var bytesWritten: Long = 0L
) {
    fun acceptBytes(bytes: ByteArray, offset: Int, length: Int) {
        bytesWritten += length
        hasher.update(bytes, offset, length)
    }

    fun hasExpectedHash(): Boolean {
        return bytesWritten == expectedBytes &&
            hasher.digestHex().equals(expectedSha256, ignoreCase = true)
    }
}

private fun RawTcpSendResult.Failure.toHttpTransportFailure(): HttpTransportResult.Failure {
    val error = when (reason) {
        RawTcpFailure.SOURCE_READ_FAILED -> HttpTransportError.SOURCE_READ_FAILED
        RawTcpFailure.HASH_MISMATCH,
        RawTcpFailure.SIZE_MISMATCH -> HttpTransportError.INTEGRITY_FAILED
        RawTcpFailure.RECEIVER_STORAGE_FULL -> HttpTransportError.REMOTE_STORAGE_FULL
        RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE -> HttpTransportError.REMOTE_STORAGE_UNAVAILABLE
        RawTcpFailure.RECEIVER_WRITE_FAILED -> HttpTransportError.UNKNOWN
        RawTcpFailure.TIMEOUT,
        RawTcpFailure.CONNECT_TIMEOUT,
        RawTcpFailure.READ_TIMEOUT -> HttpTransportError.TIMEOUT
        RawTcpFailure.CANCELLED -> HttpTransportError.RECEIVER_CANCELLED
        RawTcpFailure.WRITE_FAILED,
        RawTcpFailure.PARTIAL_TRANSFER -> HttpTransportError.TRANSFER_INTERRUPTED
        RawTcpFailure.LISTENER_START_FAILED,
        RawTcpFailure.PORT_BIND_FAILED,
        RawTcpFailure.ACCEPT_TIMEOUT,
        RawTcpFailure.CONNECTION_REFUSED,
        RawTcpFailure.RECEIVER_UNAVAILABLE,
        RawTcpFailure.TOKEN_INVALID,
        RawTcpFailure.HEADER_INVALID,
        RawTcpFailure.IO_ERROR -> HttpTransportError.UNREACHABLE
    }
    return HttpTransportResult.Failure(
        error = error,
        detail = "rawTcpFailure=${reason.name} bytesSent=$bytesSent"
    )
}

