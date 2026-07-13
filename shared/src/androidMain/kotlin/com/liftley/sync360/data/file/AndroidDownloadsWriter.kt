package com.liftley.sync360.data.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

class AndroidDownloadsWriter(
    private val context: Context
) {
    fun writeFile(
        fileName: String,
        mimeType: String?,
        fileSizeBytes: Long,
        input: InputStream
    ): Uri {
        val safeFileName = File(fileName).name

        val fileDetails = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeFileName)
            put(
                MediaStore.Downloads.MIME_TYPE,
                mimeType ?: "application/octet-stream"
            )
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val contentResolver = context.contentResolver

        val destinationUri = contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            fileDetails
        ) ?: error("Could not create $safeFileName in Downloads")

        try {
            val output = contentResolver.openOutputStream(destinationUri)
                ?: error("Could not open $safeFileName for writing")

            output.use {
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                var bytesRemaining = fileSizeBytes

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
                        error("Connection ended before $safeFileName was complete")
                    }

                    it.write(buffer, 0, bytesRead)
                    bytesRemaining -= bytesRead
                }

                it.flush()
            }

            contentResolver.update(
                destinationUri,
                ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
                null,
                null
            )

            return destinationUri
        } catch (exception: Exception) {
            contentResolver.delete(destinationUri, null, null)
            throw exception
        }
    }

    private companion object {
        const val BUFFER_SIZE_BYTES = 64 * 1024
    }
}
