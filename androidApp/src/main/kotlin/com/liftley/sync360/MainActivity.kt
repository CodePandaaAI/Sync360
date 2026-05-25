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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import org.koin.android.ext.android.inject
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import com.liftley.sync360.features.sync.domain.usecase.ClearAllDataUseCase

class MainActivity : ComponentActivity() {

    private val platformOps: PlatformOperations by inject()
    private val disconnectAllUseCase: DisconnectAllUseCase by inject()
    private val clearAllDataUseCase: ClearAllDataUseCase by inject()
    private var onFilePickedCallback: ((name: String, mimeType: String, content: ByteArray) -> Unit)? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            handleFilePicked(uri)
        }

    private val pickVisualMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            handleFilePicked(uri)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)


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
        }

        setContent {
            App(isDesktop = false)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            runBlocking {
                disconnectAllUseCase.invoke()
                clearAllDataUseCase.invoke()
            }
        }
        super.onDestroy()
    }

    private fun handleFilePicked(uri: Uri?) {
        val callback = onFilePickedCallback ?: return
        if (uri == null) {
            onFilePickedCallback = null
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = applicationContext.contentResolver
                var fileName = "file_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) {
                            val size = cursor.getLong(sizeIndex)
                            if (size > 50 * 1024 * 1024) {
                                launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "File exceeds 50MB limit",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@launch
                            }
                        }
                    }
                }
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) callback(fileName, mimeType, bytes)
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to read file: ${e.message}",
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
                    resolver.openOutputStream(uri)?.use { it.write(content) }
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
