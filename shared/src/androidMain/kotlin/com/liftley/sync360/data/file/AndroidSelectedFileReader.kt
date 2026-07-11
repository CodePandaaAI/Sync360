package com.liftley.sync360.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.liftley.sync360.domain.model.SelectedFile

class AndroidSelectedFileReader(
    private val context: Context
) : SelectedFileReader {

    override fun readSelectedFiles(platformFiles: List<Any>): List<SelectedFile> {
        val selectedUris = platformFiles.filterIsInstance<Uri>()
        val contentResolver = context.contentResolver

        return selectedUris.map { uri ->
            var displayName = "Unknown_File"
            var sizeBytes: Long? = null

            val requestedColumns = arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
            )

            contentResolver.query(
                uri,
                requestedColumns,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (nameColumn != -1) {
                        displayName = cursor.getStringOrNull(nameColumn) ?: "Unknown_File"
                    }

                    if (sizeColumn != -1) {
                        sizeBytes = cursor.getLongOrNull(sizeColumn)
                    }
                }
            }

            SelectedFile(
                uri = uri.toString(),
                displayName = displayName,
                sizeBytes = sizeBytes,
                mimeType = contentResolver.getType(uri)
            )
        }
    }
}
