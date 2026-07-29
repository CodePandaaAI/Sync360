package com.liftley.sync360.data.file

import com.liftley.sync360.data.network.tcp.FileTransferConstants
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeout
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSOutputStream
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
class IosDocumentsStorage {
    suspend fun writeFile(
        fileName: String,
        fileSizeBytes: Long,
        input: ByteReadChannel,
        onBytesWritten: (byteCount: Int) -> Unit
    ) {
        val fileManager = NSFileManager.defaultManager
        val documentsUrl = iosDownloadsDirectoryUrl()

        val safeFileName = safeFileName(fileName)
        val temporaryUrl = documentsUrl.URLByAppendingPathComponent(
            ".sync360-${NSUUID().UUIDString}.part"
        ) ?: error("Could not create a temporary file URL")
        val temporaryPath = temporaryUrl.path
            ?: error("Could not create a temporary file path")

        try {
            val output = NSOutputStream.outputStreamToFileAtPath(
                path = temporaryPath,
                append = false
            )
            output.open()

            try {
                val buffer = ByteArray(FileTransferConstants.PAYLOAD_BUFFER_SIZE_BYTES)
                var bytesRemaining = fileSizeBytes

                while (bytesRemaining > 0) {
                    val bytesRequested = minOf(
                        buffer.size.toLong(),
                        bytesRemaining
                    ).toInt()
                    val bytesRead = withTimeout(
                        FileTransferConstants.SOCKET_TIMEOUT_MILLIS.toLong()
                    ) {
                        input.readAvailable(
                            buffer = buffer,
                            offset = 0,
                            length = bytesRequested
                        )
                    }

                    if (bytesRead == -1) {
                        error("Connection ended before $safeFileName was complete")
                    }
                    if (bytesRead == 0) {
                        continue
                    }

                    writeFully(output, buffer, bytesRead)
                    bytesRemaining -= bytesRead
                    onBytesWritten(bytesRead)
                }
            } finally {
                output.close()
            }

            val destinationUrl = availableDestination(
                documentsUrl = documentsUrl,
                fileName = safeFileName
            )
            check(
                fileManager.moveItemAtURL(
                    srcURL = temporaryUrl,
                    toURL = destinationUrl,
                    error = null
                )
            ) {
                "Could not move $safeFileName into the iOS Documents directory"
            }
        } catch (exception: Throwable) {
            fileManager.removeItemAtURL(temporaryUrl, error = null)
            throw exception
        }
    }

    private fun writeFully(
        output: NSOutputStream,
        buffer: ByteArray,
        byteCount: Int
    ) {
        buffer.usePinned { pinnedBuffer ->
            var offset = 0

            while (offset < byteCount) {
                val written = output.write(
                    buffer = pinnedBuffer.addressOf(offset).reinterpret(),
                    maxLength = (byteCount - offset).toULong()
                )

                if (written <= 0) {
                    error(
                        output.streamError?.localizedDescription
                            ?: "Could not write the received file"
                    )
                }

                offset += written.toInt()
            }
        }
    }

    private fun availableDestination(
        documentsUrl: NSURL,
        fileName: String
    ): NSURL {
        val fileManager = NSFileManager.defaultManager
        val requestedUrl = documentsUrl.URLByAppendingPathComponent(fileName)
            ?: error("Could not create the destination file URL")
        val requestedPath = requestedUrl.path
            ?: error("Could not create the destination file path")

        if (!fileManager.fileExistsAtPath(requestedPath)) {
            return requestedUrl
        }

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
            val candidateUrl = documentsUrl.URLByAppendingPathComponent(
                "$nameWithoutExtension ($copyNumber)$extension"
            ) ?: error("Could not create the destination file URL")
            val candidatePath = candidateUrl.path
                ?: error("Could not create the destination file path")

            if (!fileManager.fileExistsAtPath(candidatePath)) {
                return candidateUrl
            }

            copyNumber++
        }
    }

    private fun safeFileName(fileName: String): String {
        val leafName = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()

        return leafName.takeUnless { it.isBlank() || it == "." || it == ".." }
            ?: "received_file"
    }
}
