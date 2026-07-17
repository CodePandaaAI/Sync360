package com.liftley.sync360.data.file

import com.liftley.sync360.data.network.tcp.FileTransferConstants
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class JvmDownloadsWriter : DownloadsWriter<InputStream> {
    override fun writeFile(
        fileName: String,
        mimeType: String?,
        fileSizeBytes: Long,
        input: InputStream
    ) {
        val downloadsDirectory = Path.of(
            System.getProperty("user.home"),
            "Downloads"
        )
        Files.createDirectories(downloadsDirectory)

        val safeFileName = File(fileName).name.ifBlank { "received_file" }
        val temporaryFile = Files.createTempFile(
            downloadsDirectory,
            ".sync360-",
            ".part"
        )

        try {
            Files.newOutputStream(temporaryFile)
                .buffered(FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES)
                .use { output ->
                    val buffer = ByteArray(FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES)
                    var bytesRemaining = fileSizeBytes

                    while (bytesRemaining > 0) {
                        val bytesRequested = minOf(
                            buffer.size.toLong(),
                            bytesRemaining
                        ).toInt()
                        val bytesRead = input.read(buffer, 0, bytesRequested)

                        if (bytesRead == -1) {
                            error("Connection ended before $safeFileName was complete")
                        }

                        output.write(buffer, 0, bytesRead)
                        bytesRemaining -= bytesRead
                    }

                    output.flush()
                }

            val destination = availableDestination(downloadsDirectory, safeFileName)
            try {
                Files.move(
                    temporaryFile,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile, destination)
            }
        } catch (exception: Exception) {
            Files.deleteIfExists(temporaryFile)
            throw exception
        }
    }

    private fun availableDestination(directory: Path, fileName: String): Path {
        val requestedPath = directory.resolve(fileName)
        if (Files.notExists(requestedPath)) return requestedPath

        val extensionIndex = fileName.lastIndexOf('.')
        val hasExtension = extensionIndex > 0
        val nameWithoutExtension = if (hasExtension) {
            fileName.substring(0, extensionIndex)
        } else {
            fileName
        }
        val extension = if (hasExtension) fileName.substring(extensionIndex) else ""

        var copyNumber = 1
        while (true) {
            val candidate = directory.resolve("$nameWithoutExtension ($copyNumber)$extension")
            if (Files.notExists(candidate)) return candidate
            copyNumber++
        }
    }
}
