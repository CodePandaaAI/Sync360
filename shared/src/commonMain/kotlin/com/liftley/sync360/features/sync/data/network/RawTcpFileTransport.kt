package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.PickedFile

object RawTcpFileTransferConfig {
    const val BUFFER_BYTES = 1024 * 1024
    const val CONNECT_TIMEOUT_MILLIS = 10_000
    const val SOCKET_IDLE_TIMEOUT_MILLIS = 30_000
    const val TRANSFER_TIMEOUT_MILLIS = 5 * 60 * 1000L
    const val ACCEPT_TIMEOUT_MILLIS = 60_000
    const val TOKEN_TTL_MILLIS = 5 * 60 * 1000L
}

data class RawTcpFileHeader(
    val transferToken: String,
    val transferId: String,
    val fileIndex: Int,
    val contentLength: Long,
    val fileIdentifier: String
)

interface RawTcpFileListener {
    fun onRawFileInit(header: RawTcpFileHeader, remoteHost: String): RawTcpReceiveResult
    fun onRawFileChunk(
        offerId: String,
        fileIndex: Int,
        chunk: ByteArray,
        offset: Int,
        length: Int
    ): Boolean
    fun onRawFileComplete(offerId: String, fileIndex: Int): RawTcpReceiveResult
    fun onRawFileError(offerId: String, fileIndex: Int, failure: RawTcpFailure)
}

enum class RawTcpFailure {
    LISTENER_START_FAILED,
    PORT_BIND_FAILED,
    ACCEPT_TIMEOUT,
    CONNECTION_REFUSED,
    CONNECT_TIMEOUT,
    READ_TIMEOUT,
    WRITE_FAILED,
    TIMEOUT,
    RECEIVER_UNAVAILABLE,
    TOKEN_INVALID,
    HEADER_INVALID,
    PARTIAL_TRANSFER,
    SIZE_MISMATCH,
    HASH_MISMATCH,
    RECEIVER_STORAGE_FULL,
    RECEIVER_STORAGE_UNAVAILABLE,
    RECEIVER_WRITE_FAILED,
    SOURCE_READ_FAILED,
    CANCELLED,
    IO_ERROR
}

sealed interface RawTcpReceiveResult {
    data object Success : RawTcpReceiveResult
    data class Failure(val reason: RawTcpFailure) : RawTcpReceiveResult
}

sealed interface RawTcpSendResult {
    data class Success(val bytesSent: Long) : RawTcpSendResult
    data class Failure(
        val reason: RawTcpFailure,
        val bytesSent: Long
    ) : RawTcpSendResult
}

sealed interface RawTcpListenerStartResult {
    data class Success(val port: Int) : RawTcpListenerStartResult
    data class Failure(val reason: RawTcpFailure) : RawTcpListenerStartResult
}

interface RawFileByteSender {
    suspend fun send(
        host: String,
        port: Int,
        header: RawTcpFileHeader,
        file: PickedFile,
        platformOperations: PlatformOperations,
        onBytesSent: (Long) -> Unit
    ): RawTcpSendResult
}

expect class RawTcpFileTransport() : RawFileByteSender {
    var listener: RawTcpFileListener?

    fun startDynamic(): RawTcpListenerStartResult
    fun closeActiveConnections()
    fun stop()

    override suspend fun send(
        host: String,
        port: Int,
        header: RawTcpFileHeader,
        file: PickedFile,
        platformOperations: PlatformOperations,
        onBytesSent: (Long) -> Unit
    ): RawTcpSendResult
}
