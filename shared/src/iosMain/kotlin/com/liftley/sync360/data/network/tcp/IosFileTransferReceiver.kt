package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.data.file.IosDocumentsStorage
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.domain.model.FileTransferProgress
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readInt
import io.ktor.utils.io.readLong
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
class IosFileTransferReceiver(
    private val documentsStorage: IosDocumentsStorage
) : FileTransferReceiver {
    private val receiverScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
    private val selectorManager = SelectorManager(Dispatchers.Default)
    private val stateLock = NSLock()
    private var serverSocket: ServerSocket? = null
    private var expectedTransfer: ExpectedTransfer? = null

    override var port: Int = 0
        private set

    override suspend fun start(): Int {
        if (serverSocket != null) {
            return port
        }

        val startedServerSocket = aSocket(selectorManager)
            .tcp()
            .bind(hostname = "0.0.0.0", port = 0)

        serverSocket = startedServerSocket
        port = startedServerSocket.port

        receiverScope.launch {
            while (isActive) {
                try {
                    receiveTransfer(startedServerSocket.accept())
                } catch (exception: Exception) {
                    if (isActive) {
                        println("iOS file receiver failed: ${exception.message}")
                    }
                }
            }
        }

        return port
    }

    override fun prepareForTransfer(
        fileOffer: FileOfferRequest,
        onFileSaved: (completedFileCount: Int) -> Unit,
        onProgress: (FileTransferProgress) -> Unit,
        onTransferFinished: suspend (wasSuccessful: Boolean) -> Unit
    ) {
        locked {
            check(expectedTransfer == null) { "Another file transfer is already expected" }

            val transfer = ExpectedTransfer(
                offer = fileOffer,
                onFileSaved = onFileSaved,
                onProgress = onProgress,
                onTransferFinished = onTransferFinished
            )
            expectedTransfer = transfer
            startWaitingForSenderTimeout(transfer)
        }
    }

    override suspend fun cancelCurrentTransfer(operationId: Uuid) {
        val cancellation = locked {
            val transfer = expectedTransfer
                ?.takeIf { it.offer.operationId == operationId }
                ?: return

            TransferCancellation(
                socket = transfer.connectedSocket,
                completionCallback = clearTransferStateLocked(transfer)
            )
        }

        runCatching { cancellation.socket?.close() }
        cancellation.completionCallback?.invoke(false)
    }

    private suspend fun receiveTransfer(senderSocket: Socket) {
        try {
            val socketInput = senderSocket.openReadChannel()
            val socketOutput = senderSocket.openWriteChannel(autoFlush = false)
            var completedFileCount = 0
            var connectedTransfer: ExpectedTransfer? = null
            var claimedTransfer: ExpectedTransfer? = null

            try {
                val transfer = locked {
                    val expected = expectedTransfer
                        ?: error("No accepted file offer is waiting")
                    check(expected.connectedSocket == null) {
                        "Another file transfer socket is already connected"
                    }
                    expected.connectedSocket = senderSocket
                    expected
                }
                connectedTransfer = transfer
                verifyOperationId(socketInput, transfer.offer.operationId)

                claimedTransfer = locked {
                    if (
                        expectedTransfer !== transfer ||
                        transfer.connectedSocket !== senderSocket
                    ) {
                        null
                    } else {
                        transfer.waitingForSenderTimeout?.cancel()
                        transfer.waitingForSenderTimeout = null
                        transfer.socketWasClaimed = true
                        transfer
                    }
                }
                val acceptedTransfer = claimedTransfer ?: return
                val progressTracker = FileTransferProgressTracker(
                    totalBytes = acceptedTransfer.offer.totalSizeBytes,
                    onProgress = acceptedTransfer.onProgress
                )

                acceptedTransfer.offer.offeredFiles.forEach { expectedFile ->
                    val receivedFileIndex = withTimeout(
                        FileTransferConstants.SOCKET_TIMEOUT_MILLIS.milliseconds
                    ) {
                        socketInput.readInt()
                    }
                    val receivedFileSize = withTimeout(
                        FileTransferConstants.SOCKET_TIMEOUT_MILLIS.milliseconds
                    ) {
                        socketInput.readLong()
                    }

                    if (receivedFileIndex != expectedFile.index) {
                        error(
                            "Expected file index ${expectedFile.index}, " +
                                "but received $receivedFileIndex"
                        )
                    }
                    if (receivedFileSize != expectedFile.fileSizeBytes) {
                        error("File size does not match the accepted offer")
                    }

                    documentsStorage.writeFile(
                        fileName = expectedFile.fileName,
                        fileSizeBytes = expectedFile.fileSizeBytes,
                        input = socketInput,
                        onBytesWritten = progressTracker::addBytes
                    )

                    completedFileCount++
                    acceptedTransfer.onFileSaved(completedFileCount)
                }

                socketOutput.writeByte(1.toByte())
                socketOutput.writeInt(completedFileCount)
                socketOutput.flush()
                finishTransfer(
                    transfer = acceptedTransfer,
                    wasSuccessful = true
                )
            } catch (exception: Exception) {
                val failedTransfer = claimedTransfer
                val transferAtFailure = connectedTransfer
                if (failedTransfer == null && transferAtFailure != null) {
                    releaseUnclaimedSocket(transferAtFailure, senderSocket)
                }
                if (
                    locked {
                        when {
                            failedTransfer != null -> expectedTransfer === failedTransfer
                            transferAtFailure != null -> expectedTransfer === transferAtFailure
                            else -> true
                        }
                    }
                ) {
                    println("iOS file transfer failed: ${exception.message}")
                }

                runCatching {
                    socketOutput.writeByte(0.toByte())
                    socketOutput.writeInt(completedFileCount)
                    socketOutput.flush()
                }

                failedTransfer?.let { transfer ->
                    finishTransfer(
                        transfer = transfer,
                        wasSuccessful = false
                    )
                }
            }
        } finally {
            senderSocket.close()
        }
    }

    private suspend fun verifyOperationId(
        input: ByteReadChannel,
        expectedOperationId: Uuid
    ) {
        val bytes = ByteArray(Uuid.SIZE_BYTES)

        withTimeout(FileTransferConstants.SOCKET_TIMEOUT_MILLIS.milliseconds) {
            var offset = 0

            while (offset < bytes.size) {
                val bytesRead = input.readAvailable(
                    buffer = bytes,
                    offset = offset,
                    length = bytes.size - offset
                )

                if (bytesRead == -1) {
                    error("Connection ended before the operation ID was received")
                }

                if (bytesRead > 0) {
                    offset += bytesRead
                }
            }
        }

        check(Uuid.fromByteArray(bytes) == expectedOperationId) {
            "File transfer operation ID does not match the accepted offer"
        }
    }

    private suspend fun finishTransfer(
        transfer: ExpectedTransfer,
        wasSuccessful: Boolean
    ) {
        val completionCallback = locked {
            clearTransferStateLocked(transfer)
        }

        completionCallback?.invoke(wasSuccessful)
    }

    private fun clearTransferStateLocked(
        transfer: ExpectedTransfer
    ): (suspend (Boolean) -> Unit)? {
        if (expectedTransfer !== transfer) return null

        expectedTransfer = null
        transfer.waitingForSenderTimeout?.cancel()
        transfer.waitingForSenderTimeout = null
        transfer.connectedSocket = null
        transfer.socketWasClaimed = false
        return transfer.onTransferFinished
    }

    private fun startWaitingForSenderTimeout(transfer: ExpectedTransfer) {
        transfer.waitingForSenderTimeout = receiverScope.launch {
            delay(
                FileTransferConstants.WAITING_FOR_FIRST_FILE_TIMEOUT_MILLIS.milliseconds
            )
            expireWaitingTransfer(transfer)
        }
    }

    private suspend fun expireWaitingTransfer(transfer: ExpectedTransfer) {
        val cancellation = locked {
            if (expectedTransfer !== transfer || transfer.socketWasClaimed) {
                return
            }

            TransferCancellation(
                socket = transfer.connectedSocket,
                completionCallback = clearTransferStateLocked(transfer)
            )
        }
        runCatching { cancellation.socket?.close() }
        cancellation.completionCallback?.invoke(false)
    }

    private fun releaseUnclaimedSocket(
        transfer: ExpectedTransfer,
        socket: Socket
    ) {
        locked {
            if (
                expectedTransfer === transfer &&
                !transfer.socketWasClaimed &&
                transfer.connectedSocket === socket
            ) {
                transfer.connectedSocket = null
            }
        }
    }

    private inline fun <T> locked(block: () -> T): T {
        stateLock.lock()
        return try {
            block()
        } finally {
            stateLock.unlock()
        }
    }

    private class ExpectedTransfer(
        val offer: FileOfferRequest,
        val onFileSaved: (completedFileCount: Int) -> Unit,
        val onProgress: (FileTransferProgress) -> Unit,
        val onTransferFinished: suspend (wasSuccessful: Boolean) -> Unit,
        var connectedSocket: Socket? = null,
        var socketWasClaimed: Boolean = false,
        var waitingForSenderTimeout: Job? = null
    )

    private data class TransferCancellation(
        val socket: Socket?,
        val completionCallback: (suspend (Boolean) -> Unit)?
    )
}
