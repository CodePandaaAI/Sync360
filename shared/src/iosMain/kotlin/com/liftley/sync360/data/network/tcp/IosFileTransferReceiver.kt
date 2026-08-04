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
    private var expectedFileOffer: FileOfferRequest? = null
    private var onFileSaved: ((completedFileCount: Int) -> Unit)? = null
    private var onProgress: ((FileTransferProgress) -> Unit)? = null
    private var onTransferFinished: ((wasSuccessful: Boolean) -> Unit)? = null
    private var waitingForSenderTimeout: Job? = null
    private var waitingForSenderGeneration = 0L

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
        onTransferFinished: (wasSuccessful: Boolean) -> Unit
    ) {
        locked {
            expectedFileOffer = fileOffer
            this.onFileSaved = onFileSaved
            this.onProgress = onProgress
            this.onTransferFinished = onTransferFinished
            startWaitingForSenderTimeout()
        }
    }

    override fun clearExpectedTransfer() {
        locked {
            waitingForSenderTimeout?.cancel()
            waitingForSenderGeneration++
            expectedFileOffer = null
            onFileSaved = null
            onProgress = null
            onTransferFinished = null
        }
    }

    private suspend fun receiveTransfer(senderSocket: Socket) {
        val fileOffer = locked {
            waitingForSenderTimeout?.cancel()
            waitingForSenderGeneration++
            expectedFileOffer
        }

        try {
            val socketInput = senderSocket.openReadChannel()
            val socketOutput = senderSocket.openWriteChannel(autoFlush = false)
            var completedFileCount = 0

            try {
                val acceptedFileOffer = fileOffer
                    ?: error("No accepted file offer is waiting")
                val progressTracker = FileTransferProgressTracker(
                    totalBytes = acceptedFileOffer.totalSizeBytes,
                    onProgress = { progress ->
                        val callback = locked { onProgress }
                        callback?.invoke(progress)
                    }
                )

                acceptedFileOffer.files.forEach { expectedFile ->
                    val receivedFileIndex = withTimeout(
                        FileTransferConstants.SOCKET_TIMEOUT_MILLIS.toLong()
                    ) {
                        socketInput.readInt()
                    }
                    val receivedFileSize = withTimeout(
                        FileTransferConstants.SOCKET_TIMEOUT_MILLIS.toLong()
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
                    val callback = locked { onFileSaved }
                    callback?.invoke(completedFileCount)
                }

                socketOutput.writeByte(1.toByte())
                socketOutput.writeInt(completedFileCount)
                socketOutput.flush()
                finishTransfer(wasSuccessful = true)
            } catch (exception: Exception) {
                println("iOS file transfer failed: ${exception.message}")

                runCatching {
                    socketOutput.writeByte(0.toByte())
                    socketOutput.writeInt(completedFileCount)
                    socketOutput.flush()
                }

                finishTransfer(wasSuccessful = false)
            }
        } finally {
            senderSocket.close()
        }
    }

    private fun finishTransfer(wasSuccessful: Boolean) {
        val completionCallback = locked {
            val callback = onTransferFinished
            waitingForSenderTimeout?.cancel()
            waitingForSenderGeneration++
            expectedFileOffer = null
            onFileSaved = null
            onProgress = null
            onTransferFinished = null
            callback
        }

        completionCallback?.invoke(wasSuccessful)
    }

    private fun startWaitingForSenderTimeout() {
        waitingForSenderTimeout?.cancel()
        waitingForSenderGeneration++
        val generation = waitingForSenderGeneration

        waitingForSenderTimeout = receiverScope.launch {
            delay(
                FileTransferConstants.WAITING_FOR_FIRST_FILE_TIMEOUT_MILLIS.milliseconds
            )
            val timeoutIsCurrent = locked {
                waitingForSenderGeneration == generation &&
                    expectedFileOffer != null
            }
            if (timeoutIsCurrent) {
                finishTransfer(wasSuccessful = false)
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
}
