package com.liftley.sync360.data.file

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.liftley.sync360.domain.repository.DownloadsFolderOpener

class AndroidDownloadsFolderOpener(
    private val context: Context
) : DownloadsFolderOpener {

    override fun openDownloads() {
        val openDownloadsIntent = Intent(
            DownloadManager.ACTION_VIEW_DOWNLOADS
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(openDownloadsIntent)
        } catch (_: ActivityNotFoundException) {
            // Some Android devices do not provide a system Downloads screen.
        } catch (_: SecurityException) {
            // Some Android devices do not provide a system Downloads screen.
        }
    }
}
