package com.liftley.sync360.data.network.tcp

import android.content.Context
import androidx.core.net.toUri
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.SelectedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

class AndroidFileTransferSender(
    private val context: Context
) : FileTransferSender {

    override suspend fun sendFiles(
        device: NearbyDevice,
        files: List<SelectedFile>,
        onFileStarted: suspend (fileIndex: Int, file: SelectedFile) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            files.forEachIndexed { fileIndex, file ->
                onFileStarted(fileIndex, file)

                sendOneFile(
                    device = device,
                    fileIndex = fileIndex,
                    file = file
                )
            }

            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun sendOneFile(
        device: NearbyDevice,
        fileIndex: Int,
        file: SelectedFile
    ) {
        val fileSize = file.sizeBytes
            ?: error("File size is unknown: ${file.displayName}")

        val fileInput = context.contentResolver.openInputStream(
            file.uri.toUri()
        ) ?: error("Could not open file: ${file.displayName}")

        fileInput.use { input ->
            Socket(
                device.hostAddresses.first(),
                device.fileTransferPort
            ).use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MILLIS

                val socketOutput = DataOutputStream(
                    BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE_BYTES)
                )

                val socketInput = DataInputStream(
                    BufferedInputStream(socket.getInputStream(), BUFFER_SIZE_BYTES)
                )

                socketOutput.writeInt(fileIndex)
                socketOutput.writeLong(fileSize)

                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                var bytesRemaining = fileSize

                while (bytesRemaining > 0) {
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
    }

    private companion object {
        const val BUFFER_SIZE_BYTES = 64 * 1024
        const val SOCKET_TIMEOUT_MILLIS = 60_000
    }
}
