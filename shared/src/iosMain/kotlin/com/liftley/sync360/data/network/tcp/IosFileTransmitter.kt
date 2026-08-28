package com.liftley.sync360.data.network.tcp

import com.liftley.sync360.domain.model.FileTransferProgress
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeInt
import io.ktor.utils.io.writeLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSLock
import platform.Foundation.NSInputStream
import platform.Foundation.NSURL
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
class IosFileTransmitter : FileTransmitter {
    private val selectorManager = SelectorManager(Dispatchers.Default)
    private val stateLock = NSLock()
    private var activeSocket: Socket? = null

    override fun cancelCurrentFileTransfer() {
        val socket = locked {
            val currentSocket = activeSocket
            activeSocket = null
            currentSocket
        }
        runCatching { socket?.close() }
    }

    override suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        files: List<SelectedFile>,
        operationId: Uuid,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit,
        onProgress: (FileTransferProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            require(files.isNotEmpty()) { "No files were selected" }

            val socket = connectToDevice(deviceToSendFiles)
            locked {
                activeSocket = socket
            }

            try {
                val socketOutput = socket.openWriteChannel(autoFlush = false)
                val socketInput = socket.openReadChannel()
                val buffer = ByteArray(FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES)
                val progressTracker = FileTransferProgressTracker(
                    totalBytes = files.sumOf { file -> requireNotNull(file.sizeBytes) },
                    onProgress = onProgress
                )

                socketOutput.writeFully(operationId.toByteArray())

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

                val receiverSavedTransferSuccessfully = socketInput.readByte().toInt() != 0
                val completedFileCount = socketInput.readInt()

                check(receiverSavedTransferSuccessfully && completedFileCount == files.size) {
                    "Receiver saved $completedFileCount of ${files.size} files"
                }
            } finally {
                locked {
                    if (activeSocket === socket) {
                        activeSocket = null
                    }
                }
                socket.close()
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
        socketOutput: ByteWriteChannel,
        buffer: ByteArray,
        progressTracker: FileTransferProgressTracker
    ) {
        val fileSize = file.sizeBytes
            ?: error("File size is unknown: ${file.displayName}")
        val fileUrl = NSURL(string = file.uri)
            ?: error("Could not open file: ${file.displayName}")
        val hasSecurityScopedAccess = fileUrl.startAccessingSecurityScopedResource()

        try {
            val input = NSInputStream(uRL = fileUrl)
            input.open()

            try {
                socketOutput.writeInt(fileIndex)
                socketOutput.writeLong(fileSize)

                var bytesRemaining = fileSize
                while (bytesRemaining > 0) {
                    currentCoroutineContext().ensureActive()

                    val bytesRequested = minOf(
                        buffer.size.toLong(),
                        bytesRemaining
                    ).toInt()
                    val bytesRead = readFileChunk(input, buffer, bytesRequested)

                    if (bytesRead == 0) {
                        error("${file.displayName} ended before its reported size")
                    }

                    socketOutput.writeFully(
                        value = buffer,
                        startIndex = 0,
                        endIndex = bytesRead
                    )
                    bytesRemaining -= bytesRead
                    progressTracker.addBytes(bytesRead)
                }
            } finally {
                input.close()
            }
        } finally {
            if (hasSecurityScopedAccess) {
                fileUrl.stopAccessingSecurityScopedResource()
            }
        }
    }

    private fun readFileChunk(
        input: NSInputStream,
        buffer: ByteArray,
        bytesRequested: Int
    ): Int {
        val bytesRead = buffer.usePinned { pinnedBuffer ->
            input.read(
                buffer = pinnedBuffer.addressOf(0).reinterpret(),
                maxLength = bytesRequested.toULong()
            )
        }

        if (bytesRead < 0) {
            error(
                input.streamError?.localizedDescription
                    ?: "Could not read the selected file"
            )
        }

        return bytesRead.toInt()
    }

    private suspend fun connectToDevice(device: NearbyDevice): Socket {
        var lastFailure: Exception? = null

        device.hostAddresses.distinct().forEach { hostAddress ->
            currentCoroutineContext().ensureActive()

            try {
                return withTimeout(FileTransferConstants.CONNECT_TIMEOUT_MILLIS.toLong()) {
                    aSocket(selectorManager)
                        .tcp()
                        .connect(hostAddress, device.fileTransferPort) {
                            socketTimeout = FileTransferConstants.SOCKET_TIMEOUT_MILLIS.toLong()
                        }
                }
            } catch (exception: Exception) {
                currentCoroutineContext().ensureActive()
                lastFailure = exception
            }
        }

        throw lastFailure ?: error("No address is available for ${device.deviceName}")
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
