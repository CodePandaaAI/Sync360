package com.liftley.sync360

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.liftley.sync360.core.platform.AndroidActivityBridge
import com.liftley.sync360.core.platform.AndroidPlatformOperations
import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val platformOps: PlatformOperations by inject()
    private val disconnectAllUseCase: DisconnectAllUseCase by inject()
    private var onFilePickedCallback: ((files: List<PickedFile>) -> Unit)? = null
    private val activityBridge = object : AndroidActivityBridge {
        override fun openFilePicker(
            kind: FilePickerKind,
            onFilesSelected: (files: List<PickedFile>) -> Unit
        ) {
            onFilePickedCallback = onFilesSelected
            when (kind) {
                FilePickerKind.Media -> {
                    pickVisualMediaLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        )
                    )
                }

                FilePickerKind.Any -> filePickerLauncher.launch("*/*")
            }
        }

        override fun openFile(path: String) {
            this@MainActivity.openFile(path)
        }

        override fun showFileInFolder(path: String) {
            this@MainActivity.showFileInFolder(path)
        }

        override fun openDownloadsFolder() {
            this@MainActivity.openDownloadsFolder()
        }
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            handleFilesPicked(uris)
        }

    private val pickVisualMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
            handleFilesPicked(uris)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)


        (platformOps as? AndroidPlatformOperations)?.attachActivityBridge(activityBridge)

        setContent {
            App(isDesktop = false)
        }
    }

    override fun onDestroy() {
        (platformOps as? AndroidPlatformOperations)?.detachActivityBridge(activityBridge)
        onFilePickedCallback = null
        if (isFinishing) {
            disconnectAllUseCase()
        }
        super.onDestroy()
    }

    private fun handleFilesPicked(uris: List<Uri>) {
        val callback = onFilePickedCallback ?: return
        if (uris.isEmpty()) {
            onFilePickedCallback = null
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                val picked = mutableListOf<PickedFile>()
                uris.forEachIndexed { index, uri ->
                    var fileName = "file_${System.currentTimeMillis()}_$index"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) {
                                val size = cursor.getLong(sizeIndex)
                                if (size <= 0L) return@forEachIndexed
                            }
                        }
                    }
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val size = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst() && sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    } ?: 0L
                    picked += PickedFile(uri.toString(), fileName, mimeType, size)
                }
                launch(Dispatchers.Main) {
                    if (picked.isNotEmpty()) {
                        callback(picked)
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to read files: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                onFilePickedCallback = null
            }
        }
    }

    private fun openFile(path: String) {
        try {
            val uri = if (path.startsWith("content://")) {
                path.toUri()
            } else {
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "$packageName.provider",
                    java.io.File(path)
                )
            }
            val mimeType = applicationContext.contentResolver.getType(uri) ?: "*/*"
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileInFolder(path: String) {
        openDownloadsFolder() // Android Scoped Storage does not easily allow 'revealing' a specific file in a generic file manager intent. Falling back to Downloads folder.
    }

    private fun openDownloadsFolder() {
        try {
            startActivity(
                Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open downloads folder", Toast.LENGTH_SHORT).show()
        }
    }
}
