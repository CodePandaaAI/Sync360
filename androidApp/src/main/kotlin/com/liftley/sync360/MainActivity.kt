package com.liftley.sync360

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.liftley.sync360.core.platform.AndroidPlatformOperations
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import org.koin.android.ext.android.inject
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase

class MainActivity : ComponentActivity() {

    private val platformOps: PlatformOperations by inject()
    private val disconnectAllUseCase: DisconnectAllUseCase by inject()
    private var onFilePickedCallback: ((files: List<PickedFile>) -> Unit)? = null

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }


        val androidOps = platformOps as? AndroidPlatformOperations
        if (androidOps != null) {
            androidOps.onOpenFilePickerCallback = { kind, callback ->
                onFilePickedCallback = callback
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
            androidOps.onOpenFileCallback = { path -> openFile(path) }
            androidOps.onSaveFileCallback = { name, bytes, onResult ->
                saveFileToDownloads(name, bytes, onResult)
            }
            androidOps.onSaveFileChunksCallback = { name, chunks, onResult ->
                saveFileChunksToDownloads(name, chunks, onResult)
            }
        }

        setContent {
            App(isDesktop = false)
        }
    }

    override fun onDestroy() {
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

    private fun saveFileToDownloads(
        name: String,
        content: ByteArray,
        onResult: (success: Boolean, path: String?) -> Unit
    ) {
        saveFileChunksToDownloads(name, listOf(content), onResult)
    }

    private fun saveFileChunksToDownloads(
        name: String,
        chunks: List<ByteArray>,
        onResult: (success: Boolean, path: String?) -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/Sync360"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        chunks.forEach { output.write(it) }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Saved: $name", Toast.LENGTH_LONG).show()
                        onResult(true, uri.toString())
                    }
                } else {
                    throw java.io.IOException("Failed to create MediaStore entry")
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save file", Toast.LENGTH_SHORT).show()
                    onResult(false, null)
                }
            }
        }
    }
}
