package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.FileOperationResult
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.diagnostics.TransferDiagnostics
import com.liftley.sync360.features.sync.domain.diagnostics.transferExecutionContext
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.model.SyncProtocolLimits
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ConnectException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

actual class RawTcpFileTransport actual constructor() : RawFileByteSender {
    actual var listener: RawTcpFileListener? = null

    private val lock = Any()
    private val activeSockets = mutableSetOf<Socket>()
    private var serverSocket: ServerSocket? = null
    private var serverScope: CoroutineScope? = null

    actual fun startDynamic(): RawTcpListenerStartResult = synchronized(lock) {
        serverSocket?.let {
            return@synchronized RawTcpListenerStartResult.Success(it.localPort)
        }
        try {
            val socket = ServerSocket(0).apply {
                receiveBufferSize = RawTcpFileTransferConfig.BUFFER_BYTES
                soTimeout = RawTcpFileTransferConfig.ACCEPT_TIMEOUT_MILLIS
            }
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            serverSocket = socket
            serverScope = scope
            scope.launch {
                while (!socket.isClosed) {
                    try {
                        val connection = socket.accept()
                        register(connection)
                        launch { receive(connection) }
                    } catch (_: SocketTimeoutException) {
                        if (!socket.isClosed) {
                            println("SYNC360_TRANSFER outcome=raw_tcp_accept_timeout")
                        }
                    } catch (_: Exception) {
                        if (!socket.isClosed) println("RawTcpFileTransport: accept failed")
                    }
                }
            }
            println("RawTcpFileTransport started on dynamic port ${socket.localPort}")
            RawTcpListenerStartResult.Success(socket.localPort)
        } catch (error: BindException) {
            println("SYNC360_TRANSFER outcome=raw_tcp_port_bind_failed detail=${error.message}")
            serverSocket = null
            serverScope = null
            RawTcpListenerStartResult.Failure(RawTcpFailure.PORT_BIND_FAILED)
        } catch (error: Exception) {
            println("SYNC360_TRANSFER outcome=raw_tcp_listener_start_failed detail=${error.message}")
            serverSocket = null
            serverScope = null
            RawTcpListenerStartResult.Failure(RawTcpFailure.LISTENER_START_FAILED)
        }
    }

    actual fun stop() {
        val sockets = synchronized(lock) {
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
            serverSocket = null
            serverScope?.cancel()
            serverScope = null
            activeSockets.toList().also { activeSockets.clear() }
        }
        sockets.forEach { it.closeQuietly() }
        listener = null
    }

    actual override suspend fun send(
        host: String,
        port: Int,
        header: RawTcpFileHeader,
        file: PickedFile,
        platformOperations: PlatformOperations,
        onBytesSent: (Long) -> Unit
    ): RawTcpSendResult {
        return try {
            withTimeout(RawTcpFileTransferConfig.TRANSFER_TIMEOUT_MILLIS) {
                sendWithinTimeout(host, port, header, file, platformOperations, onBytesSent)
            }
        } catch (_: TimeoutCancellationException) {
            RawTcpSendResult.Failure(RawTcpFailure.TIMEOUT, 0L)
        }
    }

    private suspend fun sendWithinTimeout(
        host: String,
        port: Int,
        header: RawTcpFileHeader,
        file: PickedFile,
        platformOperations: PlatformOperations,
        onBytesSent: (Long) -> Unit
    ): RawTcpSendResult = withContext(Dispatchers.IO) {
        val totalStarted = TimeSource.Monotonic.markNow()
        var socketWriteNanos = 0L
        var sourceNanos = 0L
        var bytesSent = 0L
        var chunks = 0L
        var socket: Socket? = null
        var result: RawTcpSendResult = RawTcpSendResult.Failure(RawTcpFailure.IO_ERROR, 0L)
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) socket?.closeQuietly()
        }

        try {
            coroutineContext.ensureActive()
            socket = Socket()
            register(socket)
            socket.connect(
                InetSocketAddress(host, port),
                RawTcpFileTransferConfig.CONNECT_TIMEOUT_MILLIS
            )
            socket.tcpNoDelay = true
            socket.sendBufferSize = RawTcpFileTransferConfig.BUFFER_BYTES
            socket.soTimeout = RawTcpFileTransferConfig.SOCKET_IDLE_TIMEOUT_MILLIS
            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())
            output.writeHeader(header)
            output.flush()
            val readyFailure = input.readReadyFailure()
            if (readyFailure != null) {
                throw RawTcpTransferException(readyFailure)
            }

            val sourceStarted = TimeSource.Monotonic.markNow()
            val readResult = platformOperations.readFileChunks(
                file,
                RawTcpFileTransferConfig.BUFFER_BYTES
            ) { bytes, offset, length ->
                coroutineContext.ensureActive()
                if (bytesSent + length > header.contentLength) {
                    val remaining = (header.contentLength - bytesSent).coerceAtLeast(0L).toInt()
                    val mismatchLength = minOf(length, remaining + 1)
                    if (mismatchLength > 0) {
                        output.write(bytes, offset, mismatchLength)
                        bytesSent += mismatchLength
                    }
                    throw RawTcpTransferException(RawTcpFailure.SIZE_MISMATCH)
                }
                val writeStarted = TimeSource.Monotonic.markNow()
                output.write(bytes, offset, length)
                socketWriteNanos += writeStarted.elapsedNow().inWholeNanoseconds
                bytesSent += length
                chunks += 1
                onBytesSent(length.toLong())
            }
            sourceNanos = (
                sourceStarted.elapsedNow().inWholeNanoseconds - socketWriteNanos
                ).coerceAtLeast(0L)
            val readBytes = (readResult as? FileOperationResult.Success<*>)?.value as? Long
            if (readBytes == null) {
                result = RawTcpSendResult.Failure(RawTcpFailure.SOURCE_READ_FAILED, bytesSent)
            } else if (readBytes != header.contentLength || bytesSent != header.contentLength) {
                result = RawTcpSendResult.Failure(RawTcpFailure.SIZE_MISMATCH, bytesSent)
            } else {
                output.flush()
                socket.shutdownOutput()
                result = input.readRawAck(bytesSent)
            }
        } catch (error: RawTcpTransferException) {
            result = RawTcpSendResult.Failure(error.reason, bytesSent)
        } catch (_: ConnectException) {
            result = RawTcpSendResult.Failure(RawTcpFailure.CONNECTION_REFUSED, bytesSent)
        } catch (_: SocketTimeoutException) {
            result = RawTcpSendResult.Failure(
                if (bytesSent == 0L) RawTcpFailure.CONNECT_TIMEOUT else RawTcpFailure.READ_TIMEOUT,
                bytesSent
            )
        } catch (error: CancellationException) {
            logSender(
                header,
                bytesSent,
                chunks,
                sourceNanos,
                socketWriteNanos,
                totalStarted,
                if (error is TimeoutCancellationException) {
                    RawTcpFailure.TIMEOUT
                } else {
                    RawTcpFailure.CANCELLED
                }
            )
            throw error
        } catch (_: Exception) {
            result = RawTcpSendResult.Failure(
                if (bytesSent > 0L) RawTcpFailure.WRITE_FAILED else RawTcpFailure.IO_ERROR,
                bytesSent
            )
        } finally {
            cancellationHandle?.dispose()
            socket?.let {
                unregister(it)
                it.closeQuietly()
            }
        }
        logSender(header, bytesSent, chunks, sourceNanos, socketWriteNanos, totalStarted, result.failureOrNull())
        result
    }

    private fun receive(connection: Socket) {
        connection.use { socket ->
            socket.tcpNoDelay = true
            socket.receiveBufferSize = RawTcpFileTransferConfig.BUFFER_BYTES
            socket.soTimeout = RawTcpFileTransferConfig.SOCKET_IDLE_TIMEOUT_MILLIS
            val totalStarted = TimeSource.Monotonic.markNow()
            var header: RawTcpFileHeader? = null
            var initialized = false
            var completed = false
            var bytesReceived = 0L
            var socketReadNanos = 0L
            var chunks = 0L
            var failure: RawTcpFailure? = null
            var output: DataOutputStream? = null
            try {
                val input = DataInputStream(socket.getInputStream())
                output = DataOutputStream(socket.getOutputStream())
                header = input.readHeader()
                if (
                    header.transferToken.length != SyncProtocolLimits.SESSION_TOKEN_HEX_LENGTH ||
                    header.transferId.isBlank() ||
                    header.transferId.length > SyncProtocolLimits.MAX_OFFER_ID_LENGTH ||
                    header.fileIndex !in 0 until SyncProtocolLimits.MAX_FILES_PER_TRANSFER ||
                    header.contentLength !in 0..SyncProtocolLimits.MAX_FILE_BYTES ||
                    header.fileIdentifier.isBlank() ||
                    header.fileIdentifier.length > SyncProtocolLimits.MAX_FILE_NAME_LENGTH
                ) {
                    throw RawTcpTransferException(RawTcpFailure.HEADER_INVALID)
                }
                val activeListener = listener
                if (activeListener == null) {
                    failure = RawTcpFailure.RECEIVER_UNAVAILABLE
                    return
                }
                when (val init = activeListener.onRawFileInit(header, socket.inetAddress.hostAddress)) {
                    RawTcpReceiveResult.Success -> Unit
                    is RawTcpReceiveResult.Failure -> {
                        failure = init.reason
                        return
                    }
                }
                initialized = true
                output.writeByte(ACK_READY)
                output.flush()
                val buffer = ByteArray(RawTcpFileTransferConfig.BUFFER_BYTES)
                while (bytesReceived < header.contentLength) {
                    val target = minOf(buffer.size.toLong(), header.contentLength - bytesReceived).toInt()
                    var filled = 0
                    while (filled < target) {
                        val readStarted = TimeSource.Monotonic.markNow()
                        val read = input.read(buffer, filled, target - filled)
                        socketReadNanos += readStarted.elapsedNow().inWholeNanoseconds
                        if (read < 0) throw RawTcpTransferException(RawTcpFailure.PARTIAL_TRANSFER)
                        filled += read
                    }
                    if (!activeListener.onRawFileChunk(header.transferId, header.fileIndex, buffer, 0, filled)) {
                        throw RawTcpTransferException(RawTcpFailure.WRITE_FAILED)
                    }
                    bytesReceived += filled
                    chunks += 1
                }
                if (input.read() != -1) throw RawTcpTransferException(RawTcpFailure.SIZE_MISMATCH)
                when (val completion = activeListener.onRawFileComplete(header.transferId, header.fileIndex)) {
                    RawTcpReceiveResult.Success -> completed = true
                    is RawTcpReceiveResult.Failure -> failure = completion.reason
                }
            } catch (error: RawTcpTransferException) {
                failure = error.reason
            } catch (_: SocketTimeoutException) {
                failure = RawTcpFailure.READ_TIMEOUT
            } catch (_: Exception) {
                failure = if (bytesReceived > 0L) RawTcpFailure.PARTIAL_TRANSFER else RawTcpFailure.IO_ERROR
            } finally {
                val currentHeader = header
                if (initialized && !completed && currentHeader != null) {
                    listener?.onRawFileError(
                        currentHeader.transferId,
                        currentHeader.fileIndex,
                        failure ?: RawTcpFailure.IO_ERROR
                    )
                }
                try {
                    output?.writeByte((failure ?: RawTcpFailure.IO_ERROR).toAck(completed))
                    output?.flush()
                } catch (_: Exception) {
                }
                logReceiver(currentHeader, bytesReceived, chunks, socketReadNanos, totalStarted, failure, completed)
                unregister(socket)
            }
        }
    }

    private fun register(socket: Socket) = synchronized(lock) { activeSockets += socket }

    private fun unregister(socket: Socket) = synchronized(lock) { activeSockets -= socket }

    private fun logSender(
        header: RawTcpFileHeader,
        bytes: Long,
        chunks: Long,
        sourceNanos: Long,
        socketWriteNanos: Long,
        totalStarted: kotlin.time.TimeMark,
        failure: RawTcpFailure?
    ) {
        val details = "transferId=${header.transferId} fileIndex=${header.fileIndex}" +
            " chunks=$chunks outcome=${failure?.name ?: "success"}"
        TransferDiagnostics.log(
            "sender_raw_tcp_file_read", bytes, sourceNanos, RawTcpFileTransferConfig.BUFFER_BYTES,
            "Dispatchers.IO", true, false, false, false, false, false,
            transferExecutionContext(), details
        )
        TransferDiagnostics.log(
            "sender_raw_tcp_socket_write", bytes, socketWriteNanos, RawTcpFileTransferConfig.BUFFER_BYTES,
            "Dispatchers.IO raw TCP sender", true, false, false, false, false, false,
            transferExecutionContext(), details
        )
        TransferDiagnostics.log(
            "sender_raw_tcp_total", bytes, totalStarted.elapsedNow().inWholeNanoseconds,
            RawTcpFileTransferConfig.BUFFER_BYTES, "Dispatchers.IO raw TCP sender",
            true, false, false, false, false, false, transferExecutionContext(), details
        )
    }

    private fun logReceiver(
        header: RawTcpFileHeader?,
        bytes: Long,
        chunks: Long,
        socketReadNanos: Long,
        totalStarted: kotlin.time.TimeMark,
        failure: RawTcpFailure?,
        completed: Boolean
    ) {
        val details = "transferId=${header?.transferId} fileIndex=${header?.fileIndex}" +
            " chunks=$chunks outcome=${if (completed) "success" else failure?.name ?: "failure"}"
        TransferDiagnostics.log(
            "receiver_raw_tcp_socket_read", bytes, socketReadNanos, RawTcpFileTransferConfig.BUFFER_BYTES,
            "Dispatchers.IO raw TCP receiver", true, false, false, false, false, false,
            transferExecutionContext(), details
        )
        TransferDiagnostics.log(
            "receiver_raw_tcp_total", bytes, totalStarted.elapsedNow().inWholeNanoseconds,
            RawTcpFileTransferConfig.BUFFER_BYTES, "Dispatchers.IO raw TCP receiver",
            true, false, false, false, false, false, transferExecutionContext(), details
        )
    }

    private companion object {
        const val MAGIC = 0x53333630
        const val VERSION = 1
        const val ACK_SUCCESS = 0
        const val ACK_CONNECTION_REFUSED = 1
        const val ACK_TIMEOUT = 2
        const val ACK_RECEIVER_UNAVAILABLE = 3
        const val ACK_PARTIAL_TRANSFER = 4
        const val ACK_SIZE_MISMATCH = 5
        const val ACK_HASH_MISMATCH = 6
        const val ACK_SOURCE_READ_FAILED = 7
        const val ACK_CANCELLED = 8
        const val ACK_IO_ERROR = 9
        const val ACK_RECEIVER_STORAGE_FULL = 10
        const val ACK_RECEIVER_STORAGE_UNAVAILABLE = 11
        const val ACK_RECEIVER_WRITE_FAILED = 12
        const val ACK_READY = 13
        const val ACK_TOKEN_INVALID = 14
        const val ACK_HEADER_INVALID = 15
        const val ACK_WRITE_FAILED = 16
    }

    private fun DataOutputStream.writeHeader(header: RawTcpFileHeader) {
        writeInt(MAGIC)
        writeInt(VERSION)
        writeUTF(header.transferToken)
        writeUTF(header.transferId)
        writeInt(header.fileIndex)
        writeLong(header.contentLength)
        writeUTF(header.fileIdentifier)
    }

    private fun DataInputStream.readHeader(): RawTcpFileHeader {
        if (readInt() != MAGIC || readInt() != VERSION) {
            throw RawTcpTransferException(RawTcpFailure.HEADER_INVALID)
        }
        return RawTcpFileHeader(
            transferToken = readUTF(),
            transferId = readUTF(),
            fileIndex = readInt(),
            contentLength = readLong(),
            fileIdentifier = readUTF()
        )
    }

    private fun DataInputStream.readRawAck(bytesSent: Long): RawTcpSendResult {
        val failure = when (readUnsignedByte()) {
            ACK_SUCCESS -> null
            ACK_CONNECTION_REFUSED -> RawTcpFailure.CONNECTION_REFUSED
            ACK_TIMEOUT -> RawTcpFailure.READ_TIMEOUT
            ACK_RECEIVER_UNAVAILABLE -> RawTcpFailure.RECEIVER_UNAVAILABLE
            ACK_PARTIAL_TRANSFER -> RawTcpFailure.PARTIAL_TRANSFER
            ACK_SIZE_MISMATCH -> RawTcpFailure.SIZE_MISMATCH
            ACK_HASH_MISMATCH -> RawTcpFailure.HASH_MISMATCH
            ACK_SOURCE_READ_FAILED -> RawTcpFailure.SOURCE_READ_FAILED
            ACK_CANCELLED -> RawTcpFailure.CANCELLED
            ACK_RECEIVER_STORAGE_FULL -> RawTcpFailure.RECEIVER_STORAGE_FULL
            ACK_RECEIVER_STORAGE_UNAVAILABLE -> RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE
            ACK_RECEIVER_WRITE_FAILED -> RawTcpFailure.RECEIVER_WRITE_FAILED
            ACK_TOKEN_INVALID -> RawTcpFailure.TOKEN_INVALID
            ACK_HEADER_INVALID -> RawTcpFailure.HEADER_INVALID
            ACK_WRITE_FAILED -> RawTcpFailure.WRITE_FAILED
            else -> RawTcpFailure.IO_ERROR
        }
        return failure?.let { RawTcpSendResult.Failure(it, bytesSent) }
            ?: RawTcpSendResult.Success(bytesSent)
    }

    private fun DataInputStream.readReadyFailure(): RawTcpFailure? {
        val ack = readUnsignedByte()
        if (ack == ACK_READY) return null
        return when (ack) {
            ACK_TIMEOUT -> RawTcpFailure.TIMEOUT
            ACK_RECEIVER_UNAVAILABLE -> RawTcpFailure.RECEIVER_UNAVAILABLE
            ACK_RECEIVER_STORAGE_FULL -> RawTcpFailure.RECEIVER_STORAGE_FULL
            ACK_RECEIVER_STORAGE_UNAVAILABLE -> RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE
            ACK_RECEIVER_WRITE_FAILED -> RawTcpFailure.RECEIVER_WRITE_FAILED
            ACK_TOKEN_INVALID -> RawTcpFailure.TOKEN_INVALID
            ACK_HEADER_INVALID -> RawTcpFailure.HEADER_INVALID
            else -> RawTcpFailure.IO_ERROR
        }
    }

    private fun RawTcpFailure.toAck(completed: Boolean): Int {
        if (completed) return ACK_SUCCESS
        return when (this) {
            RawTcpFailure.CONNECTION_REFUSED -> ACK_CONNECTION_REFUSED
            RawTcpFailure.TIMEOUT -> ACK_TIMEOUT
            RawTcpFailure.RECEIVER_UNAVAILABLE -> ACK_RECEIVER_UNAVAILABLE
            RawTcpFailure.PARTIAL_TRANSFER -> ACK_PARTIAL_TRANSFER
            RawTcpFailure.SIZE_MISMATCH -> ACK_SIZE_MISMATCH
            RawTcpFailure.HASH_MISMATCH -> ACK_HASH_MISMATCH
            RawTcpFailure.RECEIVER_STORAGE_FULL -> ACK_RECEIVER_STORAGE_FULL
            RawTcpFailure.RECEIVER_STORAGE_UNAVAILABLE -> ACK_RECEIVER_STORAGE_UNAVAILABLE
            RawTcpFailure.RECEIVER_WRITE_FAILED -> ACK_RECEIVER_WRITE_FAILED
            RawTcpFailure.TOKEN_INVALID -> ACK_TOKEN_INVALID
            RawTcpFailure.HEADER_INVALID -> ACK_HEADER_INVALID
            RawTcpFailure.WRITE_FAILED -> ACK_WRITE_FAILED
            RawTcpFailure.SOURCE_READ_FAILED -> ACK_SOURCE_READ_FAILED
            RawTcpFailure.CANCELLED -> ACK_CANCELLED
            RawTcpFailure.IO_ERROR -> ACK_IO_ERROR
            RawTcpFailure.LISTENER_START_FAILED,
            RawTcpFailure.PORT_BIND_FAILED,
            RawTcpFailure.ACCEPT_TIMEOUT,
            RawTcpFailure.CONNECT_TIMEOUT,
            RawTcpFailure.READ_TIMEOUT -> ACK_IO_ERROR
        }
    }
}

private class RawTcpTransferException(val reason: RawTcpFailure) : Exception()

private fun RawTcpSendResult.failureOrNull(): RawTcpFailure? =
    (this as? RawTcpSendResult.Failure)?.reason

private fun Socket.closeQuietly() {
    try {
        close()
    } catch (_: Exception) {
    }
}
