package com.liftley.sync360.data.file

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal fun iosDownloadsDirectoryUrl(): NSURL {
    val fileManager = NSFileManager.defaultManager
    val documentsUrl = fileManager.URLsForDirectory(
        directory = NSDocumentDirectory,
        inDomains = NSUserDomainMask
    ).firstOrNull() as? NSURL
        ?: error("The iOS Documents directory is unavailable")
    val downloadsUrl = documentsUrl.URLByAppendingPathComponent(
        pathComponent = "Downloads",
        isDirectory = true
    ) ?: error("Could not create the iOS Downloads directory URL")

    check(
        fileManager.createDirectoryAtURL(
            url = downloadsUrl,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    ) {
        "Could not create the iOS Downloads directory"
    }

    return downloadsUrl
}
