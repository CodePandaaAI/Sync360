package com.liftley.sync360.data.file

import com.liftley.sync360.domain.model.SelectedFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class JvmSelectedFileReader : SelectedFileReader {
    override fun readSelectedFiles(platformFiles: List<Any>): List<SelectedFile> {
        return platformFiles.mapNotNull { platformFile ->
            val file = when (platformFile) {
                is File -> platformFile
                is Path -> platformFile.toFile()
                else -> null
            } ?: return@mapNotNull null

            if (!file.isFile) return@mapNotNull null

            SelectedFile(
                uri = file.absolutePath,
                displayName = file.name,
                sizeBytes = file.length(),
                mimeType = runCatching {
                    Files.probeContentType(file.toPath())
                }.getOrNull()
            )
        }
    }
}
