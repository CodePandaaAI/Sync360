package com.liftley.sync360.data.file

import com.liftley.sync360.domain.repository.DownloadsFolderOpener
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path

class JvmDownloadsFolderOpener : DownloadsFolderOpener {
    override fun openDownloads() {
        val downloadsDirectory = Path.of(
            System.getProperty("user.home"),
            "Downloads"
        )
        Files.createDirectories(downloadsDirectory)

        check(Desktop.isDesktopSupported()) {
            "Opening the Downloads folder is not supported on this system"
        }

        Desktop.getDesktop().open(downloadsDirectory.toFile())
    }
}
