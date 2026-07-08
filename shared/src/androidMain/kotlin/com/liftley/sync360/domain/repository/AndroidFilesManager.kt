package com.liftley.sync360.domain.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.liftley.sync360.presentation.send.model.PickedFile

class AndroidFilesManager(private val context: Context) : FilesManager {
    override fun processPickedFiles(platformPaths: List<Any>): List<PickedFile> {
        val uris = platformPaths.filterIsInstance<Uri>()
        val contentResolver = context.contentResolver

        val files = uris.map { fileUri ->
            var fileName = "Unknown_File"
            var fileSize: Long? = null
            val fileMimeType = contentResolver.getType(fileUri)

            val listOfColumnValuesRequired =
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

            contentResolver.query(fileUri, listOfColumnValuesRequired, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                        if (nameColumnIndex != -1) {
                            fileName = cursor.getStringOrNull(nameColumnIndex) ?: "Unknown_File"
                        }
                        if (sizeColumnIndex != -1) {
                            fileSize = cursor.getLongOrNull(sizeColumnIndex)
                        }
                    }
                }

            PickedFile(
                uri = fileUri.toString(),
                displayName = fileName,
                sizeBytes = fileSize,
                mimeType = fileMimeType,
            )
        }

        return files
    }
}