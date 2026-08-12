package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.data.file.DownloadsWriter
import com.liftley.sync360.data.network.http.dto.file.FileOfferRequest
import com.liftley.sync360.domain.model.FileTransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class JvmFileTransferReceiver(
    private val downloadsWriter: DownloadsWriter<InputStream>
) : FileTransferReceiver {
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null

    private var expectedTransfer: ExpectedTransfer? = null

    override var port: Int = 0
        private set

    override suspend fun start(): Int {
        if (serverSocket != null) return port

        val startedServerSocket = withContext(Dispatchers.IO) {
            ServerSocket(0)
        }

        serverSocket = startedServerSocket
        port = startedServerSocket.localPort

        receiverScope.launch {
            while (isActive) {
                try {
                    receiveTransfer(startedServerSocket.accept())
                } catch (exception: Exception) {
                    if (isActive) exception.printStackTrace()
                }
            }
        }

        return port
    }

    @Synchronized
    override fun prepareForTransfer(
        fileOffer: FileOfferRequest,
        onFileSaved: (completedFileCount: Int) -> Unit,
        onProgress: (FileTransferProgress) -> Unit,
        onTransferFinished: suspend (wasSuccessful: Boolean) -> Unit
    ) {
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

    override suspend fun cancelCurrentTransfer(operationId: Uuid) {
        val cancellation = synchronized(this) {
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
        senderSocket.use { socket ->
            socket.soTimeout = FileTransferConstants.SOCKET_TIMEOUT_MILLIS
            val socketInput = DataInputStream(
                BufferedInputStream(
                    socket.getInputStream(),
                    FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES
                )
            )
            val socketOutput = DataOutputStream(socket.getOutputStream())
            var completedFileCount = 0
            var connectedTransfer: ExpectedTransfer? = null
            var claimedTransfer: ExpectedTransfer? = null

            try {
                val transfer = synchronized(this) {
                    val expected = expectedTransfer
                        ?: error("No accepted file offer is waiting")
                    check(expected.connectedSocket == null) {
                        "Another file transfer socket is already connected"
                    }
                    expected.connectedSocket = socket
                    expected
                }
                connectedTransfer = transfer
                verifyOperationId(socketInput, transfer.offer.operationId)

                claimedTransfer = synchronized(this) {
                    if (
                        expectedTransfer !== transfer ||
                        transfer.connectedSocket !== socket
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
                    val receivedFileIndex = socketInput.readInt()
                    val receivedFileSize = socketInput.readLong()

                    check(receivedFileIndex == expectedFile.index) {
                        "Expected file index ${expectedFile.index}, but received $receivedFileIndex"
                    }
                    check(receivedFileSize == expectedFile.fileSizeBytes) {
                        "Expected file size ${expectedFile.fileSizeBytes}, but received $receivedFileSize"
                    }

                    downloadsWriter.writeFile(
                        fileName = expectedFile.fileName,
                        mimeType = expectedFile.mimeType,
                        fileSizeBytes = expectedFile.fileSizeBytes,
                        input = socketInput,
                        onBytesWritten = progressTracker::addBytes
                    )

                    completedFileCount++
                    acceptedTransfer.onFileSaved(completedFileCount)
                }

                socketOutput.writeBoolean(true)
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
                    releaseUnclaimedSocket(transferAtFailure, socket)
                }
                if (
                    synchronized(this) {
                        when {
                            failedTransfer != null -> expectedTransfer === failedTransfer
                            transferAtFailure != null -> expectedTransfer === transferAtFailure
                            else -> true
                        }
                    }
                ) {
                    exception.printStackTrace()
                }
                runCatching {
                    socketOutput.writeBoolean(false)
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
        }
    }

    private fun verifyOperationId(
        input: DataInputStream,
        expectedOperationId: Uuid
    ) {
        val bytes = ByteArray(Uuid.SIZE_BYTES)
        input.readFully(bytes)

        check(Uuid.fromByteArray(bytes) == expectedOperationId) {
            "File transfer operation ID does not match the accepted offer"
        }
    }

    private suspend fun finishTransfer(
        transfer: ExpectedTransfer,
        wasSuccessful: Boolean
    ) {
        val completionCallback = synchronized(this) {
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
            delay(FileTransferConstants.WAITING_FOR_FIRST_FILE_TIMEOUT_MILLIS.milliseconds)
            expireWaitingTransfer(transfer)
        }
    }

    private suspend fun expireWaitingTransfer(transfer: ExpectedTransfer) {
        val cancellation = synchronized(this) {
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
        synchronized(this) {
            if (
                expectedTransfer === transfer &&
                !transfer.socketWasClaimed &&
                transfer.connectedSocket === socket
            ) {
                transfer.connectedSocket = null
            }
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
