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

class JvmFileTransferReceiver(
    private val downloadsWriter: DownloadsWriter<InputStream>
) : FileTransferReceiver {
    private val receiverScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var expectedFileOffer: FileOfferRequest? = null

    private var nextExpectedFileIndex: Int = 0
    private var onFileSaved: ((completedFileCount: Int) -> Unit)? = null
    private var onProgress: ((FileTransferProgress) -> Unit)? = null
    private var onTransferFinished: ((wasSuccessful: Boolean) -> Unit)? = null
    private var waitingForSenderTimeout: Job? = null

    override var port: Int = 0
        private set

    override suspend fun start() {
        if (serverSocket != null) return

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
    }

    @Synchronized
    override fun prepareForTransfer(
        fileOffer: FileOfferRequest,
        onFileSaved: (completedFileCount: Int) -> Unit,
        onProgress: (FileTransferProgress) -> Unit,
        onTransferFinished: (wasSuccessful: Boolean) -> Unit
    ) {
        expectedFileOffer = fileOffer
        nextExpectedFileIndex = 0
        this.onFileSaved = onFileSaved
        this.onProgress = onProgress
        this.onTransferFinished = onTransferFinished
        startWaitingForSenderTimeout()
    }

    @Synchronized
    override fun clearExpectedTransfer() {
        waitingForSenderTimeout?.cancel()
        expectedFileOffer = null
        nextExpectedFileIndex = 0
        onFileSaved = null
        onProgress = null
        onTransferFinished = null
    }

    private fun receiveTransfer(senderSocket: Socket) {
        waitingForSenderTimeout?.cancel()

        senderSocket.use { socket ->
            socket.soTimeout = FileTransferConstants.SOCKET_TIMEOUT_MILLIS

            val socketInput = DataInputStream(
                BufferedInputStream(
                    socket.getInputStream(),
                    FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES
                )
            )
            val socketOutput = DataOutputStream(socket.getOutputStream())

            try {
                val fileOffer = expectedFileOffer
                    ?: error("No accepted file offer is waiting")
                val progressTracker = FileTransferProgressTracker(
                    totalBytes = fileOffer.totalSizeBytes,
                    onProgress = { progress -> onProgress?.invoke(progress) }
                )

                fileOffer.files.forEach { expectedFile ->
                    val receivedFileIndex = socketInput.readInt()
                    val receivedFileSize = socketInput.readLong()

                    if (receivedFileIndex != nextExpectedFileIndex) {
                        error(
                            "Expected file index $nextExpectedFileIndex " +
                                "but received $receivedFileIndex"
                        )
                    }
                    if (expectedFile.index != receivedFileIndex) {
                        error("File index does not match the accepted offer")
                    }
                    if (receivedFileSize != expectedFile.fileSizeBytes) {
                        error("File size does not match the accepted offer")
                    }

                    downloadsWriter.writeFile(
                        fileName = expectedFile.fileName,
                        mimeType = expectedFile.mimeType,
                        fileSizeBytes = expectedFile.fileSizeBytes,
                        input = socketInput,
                        onBytesWritten = progressTracker::addBytes
                    )

                    markCurrentFileComplete(receivedFileIndex)
                    sendSaveResult(socketOutput, wasSaved = true)
                }

                finishTransfer(wasSuccessful = true)
            } catch (exception: Exception) {
                exception.printStackTrace()

                runCatching {
                    sendSaveResult(socketOutput, wasSaved = false)
                }
                finishTransfer(wasSuccessful = false)
            }
        }
    }

    @Synchronized
    private fun markCurrentFileComplete(completedFileIndex: Int) {
        if (completedFileIndex != nextExpectedFileIndex) return

        nextExpectedFileIndex++
        onFileSaved?.invoke(nextExpectedFileIndex)
    }

    @Synchronized
    private fun finishTransfer(wasSuccessful: Boolean) {
        val completionCallback = onTransferFinished

        waitingForSenderTimeout?.cancel()
        expectedFileOffer = null
        nextExpectedFileIndex = 0
        onFileSaved = null
        onProgress = null
        onTransferFinished = null

        completionCallback?.invoke(wasSuccessful)
    }

    private fun startWaitingForSenderTimeout() {
        waitingForSenderTimeout?.cancel()
        waitingForSenderTimeout = receiverScope.launch {
            delay(
                FileTransferConstants.WAITING_FOR_FIRST_FILE_TIMEOUT_MILLIS.milliseconds
            )
            finishTransfer(wasSuccessful = false)
        }
    }

    private fun sendSaveResult(output: DataOutputStream, wasSaved: Boolean) {
        output.writeBoolean(wasSaved)
        output.flush()
    }

}
