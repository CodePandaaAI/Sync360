package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.core.platform.PlatformFileError
import com.liftley.sync360.core.security.Sha256Hasher
import com.liftley.sync360.features.sync.domain.diagnostics.TransferDiagnostics
import com.liftley.sync360.features.sync.domain.diagnostics.transferExecutionContext
import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class FileTransferManager(
    private val platformOperations: PlatformOperations
) {
    private val activeIncomingWrites = mutableMapOf<String, IncomingFileWrite>()
    private val incomingFailures = mutableMapOf<String, IncomingUploadFailure>()
    
    // Receiver progress means "bytes written into the platform file sink".
    // It is capped below 100 until the platform finalizes the file.
    private var currentIncomingTotalBytes = 0L
    private var currentIncomingWrittenBytes = 0L
    private var currentIncomingProgressCallback: ((bytes: Long) -> Unit)? = null

    fun registerIncomingTotalSize(totalBytes: Long, onProgress: (bytes: Long) -> Unit) {
        currentIncomingTotalBytes = totalBytes.coerceAtLeast(1L)
        currentIncomingWrittenBytes = 0L
        currentIncomingProgressCallback = onProgress
    }

    suspend fun prepareOutgoingFiles(files: List<PickedFile>): List<PreparedOutgoingFile>? =
        withContext(Dispatchers.IO) {
            val batchStarted = TimeSource.Monotonic.markNow()
            val prepared = mutableListOf<PreparedOutgoingFile>()
            var batchBytes = 0L
            var batchChunks = 0L
            for ((fileIndex, file) in files.withIndex()) {
                val fileStarted = TimeSource.Monotonic.markNow()
                val hasher = Sha256Hasher()
                var hashNanos = 0L
                var chunkCount = 0L
                var executionContext = transferExecutionContext()
                val read = platformOperations.readFileChunks(
                    file,
                    HASH_CHUNK_SIZE_BYTES
                ) { bytes, offset, length ->
                    executionContext = transferExecutionContext()
                    val hashStarted = TimeSource.Monotonic.markNow()
                    hasher.update(bytes, offset, length)
                    hashNanos += hashStarted.elapsedNow().inWholeNanoseconds
                    chunkCount += 1
                }
                val bytesRead = (read as? FileOperationResult.Success<*>)?.value as? Long
                val actualBytesRead = bytesRead ?: 0L
                val succeeded = actualBytesRead == file.sizeBytes
                TransferDiagnostics.log(
                    stage = "sender_prepare_hash_read",
                    bytes = actualBytesRead,
                    elapsedNanos = fileStarted.elapsedNow().inWholeNanoseconds,
                    bufferBytes = HASH_CHUNK_SIZE_BYTES,
                    dispatcher = "Dispatchers.IO",
                    streamed = true,
                    fullFileInMemory = false,
                    base64 = false,
                    stringEncoding = false,
                    json = false,
                    multipart = false,
                    executionContext = executionContext,
                    details = "fileIndex=$fileIndex chunks=$chunkCount" +
                        " hashMs=${hashNanos / NANOS_PER_MILLISECOND}" +
                        " outcome=${if (succeeded) "success" else "failure"}"
                )
                if (!succeeded) return@withContext null
                batchBytes += actualBytesRead
                batchChunks += chunkCount
                prepared += PreparedOutgoingFile(file, hasher.digestHex())
            }
            TransferDiagnostics.log(
                stage = "sender_prepare_batch",
                bytes = batchBytes,
                elapsedNanos = batchStarted.elapsedNow().inWholeNanoseconds,
                bufferBytes = HASH_CHUNK_SIZE_BYTES,
                dispatcher = "Dispatchers.IO",
                streamed = true,
                fullFileInMemory = false,
                base64 = false,
                stringEncoding = false,
                json = false,
                multipart = false,
                details = "files=${files.size} chunks=$batchChunks outcome=success"
            )
            prepared
        }

    fun initIncomingFileWrite(
        offerId: String,
        fileIndex: Int,
        fileName: String,
        expectedBytes: Long,
        expectedSha256: String,
        dispatcher: String = "Raw TCP receiver coroutine"
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
                    expectedSha256 = expectedSha256,
                    dispatcher = dispatcher,
                    executionContext = transferExecutionContext()
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

    fun writeIncomingFileChunk(offerId: String, fileIndex: Int, chunk: ByteArray, offset: Int, length: Int): Boolean {
        val key = "${offerId}_$fileIndex"
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites[key] } ?: return false
        if (write.bytesWritten + length > write.expectedBytes) {
            synchronized(incomingFailures) {
                incomingFailures[key] = IncomingUploadFailure.INTEGRITY
            }
            errorIncomingFileWrite(offerId, fileIndex)
            return false
        }

        val writeStarted = TimeSource.Monotonic.markNow()
        val wrote = platformOperations.writeFileChunk(write.handle, chunk, offset, length)
        write.platformWriteNanos += writeStarted.elapsedNow().inWholeNanoseconds
        if (wrote is FileOperationResult.Success) {
            write.bytesWritten += length
            val hashStarted = TimeSource.Monotonic.markNow()
            write.hasher.update(chunk, offset, length)
            write.hashNanos += hashStarted.elapsedNow().inWholeNanoseconds
            currentIncomingWrittenBytes += length
            val progressStarted = TimeSource.Monotonic.markNow()
            currentIncomingProgressCallback?.invoke(currentIncomingWrittenBytes)
            write.progressNanos += progressStarted.elapsedNow().inWholeNanoseconds
            write.progressCallbacks += 1
            write.chunkCount += 1
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
        val digestStarted = TimeSource.Monotonic.markNow()
        val receivedSha256 = write.hasher.digestHex()
        write.hashNanos += digestStarted.elapsedNow().inWholeNanoseconds
        if (
            write.bytesWritten != write.expectedBytes ||
            !receivedSha256.equals(write.expectedSha256, ignoreCase = true)
        ) {
            synchronized(incomingFailures) {
                incomingFailures[key] = IncomingUploadFailure.INTEGRITY
            }
            platformOperations.cancelFileWrite(write.handle)
            write.logSummary(
                offerId = offerId,
                fileIndex = fileIndex,
                outcome = "integrity_failure",
                finalizeNanos = 0L
            )
            return null
        }
        val finalizeStarted = TimeSource.Monotonic.markNow()
        val finishResult = platformOperations.finishFileWrite(write.handle)
        val finalizeNanos = finalizeStarted.elapsedNow().inWholeNanoseconds
        val path = (finishResult as? FileOperationResult.Success<*>)?.value as? String
        if (path == null) {
            val error = (finishResult as? FileOperationResult.Failure)?.error
            synchronized(incomingFailures) {
                incomingFailures[key] = error.toIncomingUploadFailure()
            }
        }
        write.logSummary(
            offerId = offerId,
            fileIndex = fileIndex,
            outcome = if (path == null) "finalize_failure" else "success",
            finalizeNanos = finalizeNanos
        )
        return path
    }

    fun errorIncomingFileWrite(offerId: String, fileIndex: Int) {
        val key = "${offerId}_$fileIndex"
        val write = synchronized(activeIncomingWrites) { activeIncomingWrites.remove(key) }
        if (write != null) {
            platformOperations.cancelFileWrite(write.handle)
            write.logSummary(
                offerId = offerId,
                fileIndex = fileIndex,
                outcome = "write_error",
                finalizeNanos = 0L
            )
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

    fun deleteFiles(paths: List<String>) {
        paths.forEach { platformOperations.deleteFile(it) }
    }

    suspend fun uploadOutgoingFilesRaw(
        rawTransport: RawFileByteSender,
        serverIp: String,
        serverPort: Int,
        offerId: String,
        transferToken: String,
        files: List<PickedFile>,
        onProgress: (bytes: Long) -> Unit
    ): HttpTransportResult = withContext(Dispatchers.IO) {
        val batchStarted = TimeSource.Monotonic.markNow()
        val totalBytes = files.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        var bytesUploaded = 0L
        var result: HttpTransportResult = HttpTransportResult.Success(200)

        files.forEachIndexed { index, file ->
            if (result is HttpTransportResult.Failure) return@forEachIndexed
            val rawResult = rawTransport.send(
                host = serverIp,
                port = serverPort,
                header = RawTcpFileHeader(
                    transferToken = transferToken,
                    transferId = offerId,
                    fileIndex = index,
                    contentLength = file.sizeBytes,
                    fileIdentifier = file.name
                ),
                file = file,
                platformOperations = platformOperations
            ) { bytesSent ->
                bytesUploaded += bytesSent
                onProgress(bytesUploaded)
            }
            if (rawResult is RawTcpSendResult.Success) {
                TransferDiagnostics.log(
                    stage = "sender_file_transport_result",
                    bytes = rawResult.bytesSent,
                    elapsedNanos = 0L,
                    bufferBytes = RawTcpFileTransferConfig.BUFFER_BYTES,
                    dispatcher = "Dispatchers.IO",
                    streamed = true,
                    fullFileInMemory = false,
                    base64 = false,
                    stringEncoding = false,
                    json = false,
                    multipart = false,
                    details = "transferId=$offerId fileIndex=$index transport=raw_tcp" +
                        " outcome=success fallbackAttempted=false"
                )
                result = HttpTransportResult.Success(200)
                return@forEachIndexed
            }

            val rawFailure = rawResult as RawTcpSendResult.Failure
            TransferDiagnostics.log(
                stage = "sender_file_transport_result",
                bytes = rawFailure.bytesSent,
                elapsedNanos = 0L,
                bufferBytes = RawTcpFileTransferConfig.BUFFER_BYTES,
                dispatcher = "Dispatchers.IO",
                streamed = true,
                fullFileInMemory = false,
                base64 = false,
                stringEncoding = false,
                json = false,
                multipart = false,
                details = "transferId=$offerId fileIndex=$index transport=raw_tcp" +
                    " outcome=${rawFailure.reason.logCode()} fallbackAttempted=false"
            )
            result = rawFailure.toHttpTransportFailure()
        }

        TransferDiagnostics.log(
            stage = "sender_raw_tcp_batch",
            bytes = bytesUploaded,
            elapsedNanos = batchStarted.elapsedNow().inWholeNanoseconds,
            bufferBytes = RawTcpFileTransferConfig.BUFFER_BYTES,
            dispatcher = "Dispatchers.IO + raw TCP socket",
            streamed = true,
            fullFileInMemory = false,
            base64 = false,
            stringEncoding = false,
            json = false,
            multipart = false,
            details = "files=${files.size} transferId=$offerId" +
                " outcome=${if (result is HttpTransportResult.Success) "success" else "failure"}"
        )
        result
    }

    private companion object {
        const val HASH_CHUNK_SIZE_BYTES = 1024 * 1024
        const val NANOS_PER_MILLISECOND = 1_000_000L
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
        RawTcpFailure.TIMEOUT -> HttpTransportError.TIMEOUT
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

private fun RawTcpFailure.logCode(): String = when (this) {
    RawTcpFailure.LISTENER_START_FAILED -> "raw_tcp_listener_start_failed"
    RawTcpFailure.PORT_BIND_FAILED -> "raw_tcp_port_bind_failed"
    RawTcpFailure.ACCEPT_TIMEOUT -> "raw_tcp_accept_timeout"
    RawTcpFailure.CONNECTION_REFUSED -> "raw_tcp_connection_refused"
    RawTcpFailure.CONNECT_TIMEOUT -> "raw_tcp_connect_timeout"
    RawTcpFailure.READ_TIMEOUT -> "raw_tcp_read_timeout"
    RawTcpFailure.WRITE_FAILED -> "raw_tcp_write_failed"
    RawTcpFailure.TOKEN_INVALID -> "raw_tcp_token_invalid"
    RawTcpFailure.HEADER_INVALID -> "raw_tcp_header_invalid"
    RawTcpFailure.SIZE_MISMATCH -> "raw_tcp_size_mismatch"
    RawTcpFailure.HASH_MISMATCH -> "raw_tcp_hash_mismatch"
    RawTcpFailure.CANCELLED -> "raw_tcp_cancelled"
    RawTcpFailure.RECEIVER_UNAVAILABLE -> "raw_tcp_receiver_unavailable"
    RawTcpFailure.PARTIAL_TRANSFER -> "raw_tcp_partial_transfer"
    RawTcpFailure.TIMEOUT -> "raw_tcp_timeout"
    RawTcpFailure.RECEIVER_STORAGE_FULL -> "raw_tcp_receiver_storage_full"
    RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE -> "raw_tcp_receiver_storage_unavailable"
    RawTcpFailure.RECEIVER_WRITE_FAILED -> "raw_tcp_receiver_write_failed"
    RawTcpFailure.SOURCE_READ_FAILED -> "raw_tcp_source_read_failed"
    RawTcpFailure.IO_ERROR -> "raw_tcp_io_error"
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
    val file: PickedFile,
    val sha256: String
)

private data class IncomingFileWrite(
    val handle: String,
    val expectedBytes: Long,
    val expectedSha256: String,
    val dispatcher: String,
    val executionContext: String,
    val hasher: Sha256Hasher = Sha256Hasher(),
    val startedAt: TimeMark = TimeSource.Monotonic.markNow(),
    var bytesWritten: Long = 0L,
    var chunkCount: Long = 0L,
    var platformWriteNanos: Long = 0L,
    var hashNanos: Long = 0L,
    var progressNanos: Long = 0L,
    var progressCallbacks: Long = 0L,
    val distinctProgressPercents: MutableSet<Int> = mutableSetOf()
)

private fun IncomingFileWrite.logSummary(
    offerId: String,
    fileIndex: Int,
    outcome: String,
    finalizeNanos: Long
) {
    val commonDetails =
        "transferId=$offerId fileIndex=$fileIndex chunks=$chunkCount outcome=$outcome"
    TransferDiagnostics.log(
        stage = "receiver_platform_write",
        bytes = bytesWritten,
        elapsedNanos = platformWriteNanos,
        bufferBytes = FILE_STREAM_BUFFER_BYTES,
        dispatcher = dispatcher,
        streamed = true,
        fullFileInMemory = false,
        base64 = false,
        stringEncoding = false,
        json = false,
        multipart = false,
        executionContext = executionContext,
        details = commonDetails
    )
    TransferDiagnostics.log(
        stage = "receiver_sha256",
        bytes = bytesWritten,
        elapsedNanos = hashNanos,
        bufferBytes = FILE_STREAM_BUFFER_BYTES,
        dispatcher = dispatcher,
        streamed = true,
        fullFileInMemory = false,
        base64 = false,
        stringEncoding = false,
        json = false,
        multipart = false,
        executionContext = executionContext,
        details = commonDetails
    )
    TransferDiagnostics.log(
        stage = "receiver_file_finalize",
        bytes = bytesWritten,
        elapsedNanos = finalizeNanos,
        bufferBytes = FILE_STREAM_BUFFER_BYTES,
        dispatcher = dispatcher,
        streamed = true,
        fullFileInMemory = false,
        base64 = false,
        stringEncoding = false,
        json = false,
        multipart = false,
        executionContext = executionContext,
        details = commonDetails
    )
    TransferDiagnostics.log(
        stage = "receiver_progress_callbacks",
        bytes = bytesWritten,
        elapsedNanos = progressNanos,
        bufferBytes = FILE_STREAM_BUFFER_BYTES,
        dispatcher = dispatcher,
        streamed = true,
        fullFileInMemory = false,
        base64 = false,
        stringEncoding = false,
        json = false,
        multipart = false,
        executionContext = executionContext,
        details = "$commonDetails progressCallbacks=$progressCallbacks" +
            " distinctProgress=${distinctProgressPercents.size}"
    )
    TransferDiagnostics.log(
        stage = "receiver_file_total",
        bytes = bytesWritten,
        elapsedNanos = startedAt.elapsedNow().inWholeNanoseconds,
        bufferBytes = FILE_STREAM_BUFFER_BYTES,
        dispatcher = dispatcher,
        streamed = true,
        fullFileInMemory = false,
        base64 = false,
        stringEncoding = false,
        json = false,
        multipart = false,
        executionContext = executionContext,
        details = commonDetails
    )
}

private const val FILE_STREAM_BUFFER_BYTES = 1024 * 1024
