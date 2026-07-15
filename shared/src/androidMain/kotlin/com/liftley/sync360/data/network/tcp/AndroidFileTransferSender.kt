package com.liftley.sync360.data.network.tcp

import android.content.Context
import androidx.core.net.toUri
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class AndroidFileTransferSender(
    private val context: Context
) : FileTransferSender {
    private val activeSocket = AtomicReference<Socket?>(null)

    override fun cancelCurrentTransfer() {
        runCatching {
            activeSocket.getAndSet(null)?.close()
        }
    }

    override suspend fun sendFiles(
        deviceToSendFiles: NearbyDevice,
        files: List<SelectedFile>,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (files.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            Socket().use { socket ->
                activeSocket.set(socket)

                try {
                    currentCoroutineContext().ensureActive()
                    socket.connect(
                        InetSocketAddress(
                            deviceToSendFiles.hostAddresses.first(),
                            deviceToSendFiles.fileTransferPort
                        ),
                        CONNECT_TIMEOUT_MILLIS
                    )
                    socket.soTimeout = SOCKET_TIMEOUT_MILLIS

                    val socketOutput = DataOutputStream(
                        BufferedOutputStream(
                            socket.getOutputStream(),
                            FILE_BUFFER_SIZE_BYTES
                        )
                    )

                    val socketInput = DataInputStream(socket.getInputStream())
                    val buffer = ByteArray(FILE_BUFFER_SIZE_BYTES)

                    files.forEachIndexed { fileIndex, file ->
                        currentCoroutineContext().ensureActive()
                        onFileStarted(fileIndex, file)

                        sendOneFile(
                            fileIndex = fileIndex,
                            file = file,
                            socketOutput = socketOutput,
                            socketInput = socketInput,
                            buffer = buffer
                        )
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
        socketInput: DataInputStream,
        buffer: ByteArray
    ) {
        currentCoroutineContext().ensureActive()

        val fileSize = file.sizeBytes
            ?: error("File size is unknown: ${file.displayName}")

        val fileInput = context.contentResolver.openInputStream(
            file.uri.toUri()
        ) ?: error("Could not open file: ${file.displayName}")

        fileInput.use { input ->
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

                val bytesRead = input.read(
                    buffer,
                    0,
                    bytesRequested
                )

                if (bytesRead == -1) {
                    error("${file.displayName} ended before its reported size")
                }

                socketOutput.write(buffer, 0, bytesRead)
                bytesRemaining -= bytesRead
            }

            socketOutput.flush()

            val receiverSavedFile = socketInput.readBoolean()

            if (!receiverSavedFile) {
                error("Receiver could not save ${file.displayName}")
            }
        }
    }

    private companion object {
        const val FILE_BUFFER_SIZE_BYTES = 256 * 1024
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val SOCKET_TIMEOUT_MILLIS = 60_000
    }
}
