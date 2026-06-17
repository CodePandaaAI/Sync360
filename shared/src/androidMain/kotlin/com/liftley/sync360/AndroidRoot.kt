package com.liftley.sync360

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.liftley.sync360.core.platform.AndroidActivityBridge
import com.liftley.sync360.core.platform.AndroidPlatformOperations
import com.liftley.sync360.core.platform.FilePickerKind
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.PickedFile
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun AndroidRoot() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val platformOperations: PlatformOperations = koinInject()
    val disconnectAllUseCase: DisconnectAllUseCase = koinInject()
    var onFilePickedCallback by remember {
        mutableStateOf<((files: List<PickedFile>) -> Unit)?>(null)
    }

    fun handleFilesPicked(uris: List<Uri>) {
        val callback = onFilePickedCallback ?: return
        if (uris.isEmpty()) {
            onFilePickedCallback = null
            return
        }
        coroutineScope.launch {
            try {
                val picked = withContext(Dispatchers.IO) {
                    context.readPickedFiles(uris)
                }
                if (picked.isNotEmpty()) {
                    callback(picked)
                }
            } catch (error: Exception) {
                Toast.makeText(
                    context, "Failed to read files: ${error.message}", Toast.LENGTH_SHORT
                ).show()
            } finally {
                onFilePickedCallback = null
            }
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            handleFilesPicked(uris)
        }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val activityBridge = remember(activity, filePickerLauncher) {
        object : AndroidActivityBridge {
            override fun openFilePicker(
                kind: FilePickerKind, onFilesSelected: (files: List<PickedFile>) -> Unit
            ) {
                onFilePickedCallback = onFilesSelected
                when (kind) {
                    FilePickerKind.Any -> filePickerLauncher.launch("*/*")
                }
            }

            override fun openFile(path: String) {
                activity?.openFile(path)
            }

            override fun showFileInFolder(path: String) {
                activity?.showFileInFolder(path)
            }

            override fun openDownloadsFolder() {
                activity?.openDownloadsFolder()
            }
        }
    }

    DisposableEffect(platformOperations, activityBridge, lifecycleOwner, activity) {
        val androidPlatformOperations = platformOperations as? AndroidPlatformOperations
        androidPlatformOperations?.attachActivityBridge(activityBridge)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                androidPlatformOperations?.detachActivityBridge(activityBridge)
                onFilePickedCallback = null
                if (activity?.isFinishing == true) {
                    disconnectAllUseCase()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            androidPlatformOperations?.detachActivityBridge(activityBridge)
            onFilePickedCallback = null
        }
    }

    MobileSyncApp()
}

private fun Context.readPickedFiles(uris: List<Uri>): List<PickedFile> {
    val picked = mutableListOf<PickedFile>()
    uris.forEachIndexed { index, uri ->
        var fileName = "file_${System.currentTimeMillis()}_$index"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
        if (size <= 0L) return@forEachIndexed
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        picked += PickedFile(uri.toString(), fileName, mimeType, size)
    }
    return picked
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Activity.openFile(path: String) {
    try {
        val uri = if (path.startsWith("content://")) {
            path.toUri()
        } else {
            FileProvider.getUriForFile(this, "$packageName.provider", java.io.File(path))
        }
        val mimeType = contentResolver.getType(uri) ?: "*/*"
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            })
    } catch (_: Exception) {
        Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show()
    }
}

private fun Activity.showFileInFolder(path: String) {
    try {
        val file = java.io.File(path)
        val parentDir = file.parentFile ?: sync360DownloadsFolder()
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", parentDir)
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            })
    } catch (_: Exception) {
        openDownloadsFolderFallback()
    }
}

private fun Activity.openDownloadsFolder() {
    val folder = sync360DownloadsFolder()
    if (!folder.exists()) folder.mkdirs()
    showFileInFolder(java.io.File(folder, "placeholder").absolutePath)
}

private fun sync360DownloadsFolder(): java.io.File = java.io.File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Sync360"
)

private fun Activity.openDownloadsFolderFallback() {
    try {
        startActivity(
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
    } catch (_: Exception) {
        Toast.makeText(this, "Could not open folder", Toast.LENGTH_SHORT).show()
    }
}
