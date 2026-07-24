package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import com.liftley.sync360.domain.model.FileTransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class JvmFileTransferSender : FileTransferSender {
    private val activeSocket = AtomicReference<Socket?>(null)

    override fun cancelCurrentTransfer() {
        runCatching {
            activeSocket.getAndSet(null)?.close()
        }
    }

    override suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        files: List<SelectedFile>,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit,
        onProgress: (FileTransferProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (files.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            connectToDevice(deviceToSendFiles).use { socket ->
                try {
                    currentCoroutineContext().ensureActive()
                    socket.soTimeout = FileTransferConstants.SOCKET_TIMEOUT_MILLIS

                    val socketOutput = DataOutputStream(
                        BufferedOutputStream(
                            socket.getOutputStream(),
                            FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES
                        )
                    )
                    val socketInput = DataInputStream(socket.getInputStream())
                    val buffer = ByteArray(FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES)
                    val progressTracker = FileTransferProgressTracker(
                        totalBytes = files.sumOf { file -> requireNotNull(file.sizeBytes) },
                        onProgress = onProgress
                    )

                    files.forEachIndexed { fileIndex, file ->
                        currentCoroutineContext().ensureActive()
                        onFileStarted(fileIndex, file)

                        sendOneFile(
                            fileIndex = fileIndex,
                            file = file,
                            socketOutput = socketOutput,
                            buffer = buffer,
                            progressTracker = progressTracker
                        )
                    }

                    socketOutput.flush()

                    val receiverSavedTransferSuccessfully = socketInput.readBoolean()
                    val completedFileCount = socketInput.readInt()

                    check(receiverSavedTransferSuccessfully && completedFileCount == files.size) {
                        "Receiver saved $completedFileCount of ${files.size} files"
                    }
                } finally {
                    activeSocket.compareAndSet(socket, null)
                }
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            currentCoroutineContext().ensureActive()
            Result.failure(exception)
        }
    }

    private suspend fun sendOneFile(
        fileIndex: Int,
        file: SelectedFile,
        socketOutput: DataOutputStream,
        buffer: ByteArray,
        progressTracker: FileTransferProgressTracker
    ) {
        currentCoroutineContext().ensureActive()

        val fileSize = file.sizeBytes
            ?: error("File size is unknown: ${file.displayName}")
        val selectedFile = File(file.uri)

        check(selectedFile.isFile) {
            "Could not open file: ${file.displayName}"
        }

        selectedFile.inputStream().use { input ->
            currentCoroutineContext().ensureActive()

            socketOutput.writeInt(fileIndex)
            socketOutput.writeLong(fileSize)

            var bytesRemaining = fileSize
            while (bytesRemaining > 0) {
                currentCoroutineContext().ensureActive()

                val bytesRequested = minOf(
                    buffer.size.toLong(),
                    bytesRemaining
                ).toInt()
                val bytesRead = input.read(buffer, 0, bytesRequested)

                if (bytesRead == -1) {
                    error("${file.displayName} ended before its reported size")
                }

                socketOutput.write(buffer, 0, bytesRead)
                bytesRemaining -= bytesRead
                progressTracker.addBytes(bytesRead)
            }
        }
    }

    private suspend fun connectToDevice(device: NearbyDevice): Socket {
        var lastFailure: Exception? = null

        device.hostAddresses.distinct().forEach { hostAddress ->
            currentCoroutineContext().ensureActive()
            val socket = Socket()
            activeSocket.set(socket)

            try {
                socket.connect(
                    InetSocketAddress(hostAddress, device.fileTransferPort),
                    FileTransferConstants.CONNECT_TIMEOUT_MILLIS
                )
                return socket
            } catch (exception: Exception) {
                activeSocket.compareAndSet(socket, null)
                runCatching { socket.close() }
                lastFailure = exception
            }
        }

        throw lastFailure ?: error("No address is available for ${device.deviceName}")
    }
}
