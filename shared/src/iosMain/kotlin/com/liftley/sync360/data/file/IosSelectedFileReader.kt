package com.liftley.sync360.data.file

import com.liftley.sync360.domain.model.SelectedFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IosSelectedFileReader : SelectedFileReader {
    override fun readSelectedFiles(platformFiles: List<Any>): List<SelectedFile> {
        return platformFiles
            .filterIsInstance<NSURL>()
            .mapNotNull { url ->
                val uri = url.absoluteString ?: return@mapNotNull null
                val fileName = url.lastPathComponent
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unknown_File"
                val hasSecurityScopedAccess = url.startAccessingSecurityScopedResource()

                try {
                    val fileSizeBytes = url.path
                        ?.let { path ->
                            NSFileManager.defaultManager
                                .attributesOfItemAtPath(path, error = null)
                                ?.get(NSFileSize)
                        }
                        .let { it as? NSNumber }
                        ?.longLongValue

                    SelectedFile(
                        uri = uri,
                        displayName = fileName,
                        sizeBytes = fileSizeBytes,
                        mimeType = null
                    )
                } finally {
                    if (hasSecurityScopedAccess) {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
            }
    }
}
