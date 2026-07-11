package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.data.file.AndroidDownloadsWriter
import com.liftley.sync360.domain.model.FileTransferOffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class AndroidFileTransferReceiver(
    private val downloadsWriter: AndroidDownloadsWriter
) : FileTransferReceiver {

    private val receiverScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private var serverSocket: ServerSocket? = null

    @Volatile
    private var expectedFileOffer: FileTransferOffer? = null

    private var nextExpectedFileIndex: Int = 0
    private var onTransferFinished: (() -> Unit)? = null
    private var waitingForSenderTimeout: Job? = null

    override var port: Int = 0
        private set

    override suspend fun start() {
        if (serverSocket != null) {
            return
        }

        val startedServerSocket = withContext(Dispatchers.IO) {
            ServerSocket(0)
        }

        serverSocket = startedServerSocket
        port = startedServerSocket.localPort

        receiverScope.launch {
            while (isActive) {
                try {
                    val senderSocket = startedServerSocket.accept()
                    receiveOneFile(senderSocket)
                } catch (exception: Exception) {
                    if (isActive) {
                        exception.printStackTrace()
                    }
                }
            }
        }
    }

    @Synchronized
    override fun prepareForTransfer(
        fileOffer: FileTransferOffer,
        onTransferFinished: () -> Unit
    ) {
        expectedFileOffer = fileOffer
        nextExpectedFileIndex = 0
        this.onTransferFinished = onTransferFinished
        startWaitingForSenderTimeout()
    }

    @Synchronized
    override fun clearExpectedTransfer() {
        waitingForSenderTimeout?.cancel()
        expectedFileOffer = null
        nextExpectedFileIndex = 0
        onTransferFinished = null
    }

    private fun receiveOneFile(senderSocket: Socket) {
        waitingForSenderTimeout?.cancel()

        senderSocket.use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MILLIS

            val socketInput = DataInputStream(
                BufferedInputStream(socket.getInputStream(), 64 * 1024)
            )

            val socketOutput = DataOutputStream(
                BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            )

            try {
                val fileOffer = expectedFileOffer
                    ?: error("No accepted file offer is waiting")

                val receivedFileIndex = socketInput.readInt()
                val receivedFileSize = socketInput.readLong()

                if (receivedFileIndex != nextExpectedFileIndex) {
                    error(
                        "Expected file index $nextExpectedFileIndex " +
                            "but received $receivedFileIndex"
                    )
                }

                val expectedFile = fileOffer.files.getOrNull(receivedFileIndex)
                    ?: error("No offered file exists at index $receivedFileIndex")

                if (expectedFile.index != receivedFileIndex) {
                    error("File index does not match the accepted offer")
                }

                val expectedFileSize = expectedFile.fileSizeBytes

                if (receivedFileSize != expectedFileSize) {
                    error("File size does not match the accepted offer")
                }

                downloadsWriter.writeFile(
                    fileName = expectedFile.fileName,
                    mimeType = expectedFile.mimeType,
                    fileSizeBytes = expectedFileSize,
                    input = socketInput
                )

                markCurrentFileComplete(
                    completedFileIndex = receivedFileIndex,
                    totalFileCount = fileOffer.files.size
                )

                sendSaveResult(socketOutput, wasSaved = true)
            } catch (exception: Exception) {
                exception.printStackTrace()

                try {
                    sendSaveResult(socketOutput, wasSaved = false)
                } catch (_: Exception) {
                    // The sender may already have closed the socket.
                }

                finishTransfer()
            }
        }
    }

    @Synchronized
    private fun markCurrentFileComplete(
        completedFileIndex: Int,
        totalFileCount: Int
    ) {
        if (completedFileIndex != nextExpectedFileIndex) {
            return
        }

        nextExpectedFileIndex++

        if (nextExpectedFileIndex == totalFileCount) {
            finishTransfer()
        } else {
            startWaitingForSenderTimeout()
        }
    }

    @Synchronized
    private fun finishTransfer() {
        val completionCallback = onTransferFinished

        waitingForSenderTimeout?.cancel()
        expectedFileOffer = null
        nextExpectedFileIndex = 0
        onTransferFinished = null

        completionCallback?.invoke()
    }

    private fun startWaitingForSenderTimeout() {
        waitingForSenderTimeout?.cancel()

        waitingForSenderTimeout = receiverScope.launch {
            delay(WAITING_FOR_SENDER_TIMEOUT_MILLIS.milliseconds)
            finishTransfer()
        }
    }

    private fun sendSaveResult(
        output: DataOutputStream,
        wasSaved: Boolean
    ) {
        output.writeBoolean(wasSaved)
        output.flush()
    }

    private companion object {
        const val SOCKET_TIMEOUT_MILLIS = 60_000
        const val WAITING_FOR_SENDER_TIMEOUT_MILLIS = 60_000L
    }
}
