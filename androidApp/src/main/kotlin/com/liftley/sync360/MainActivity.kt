package com.liftley.sync360

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.liftley.sync360.core.network.SyncPayload
import com.liftley.sync360.core.network.SyncPayloadCodec
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.overlay.FloatingDockManager
import com.liftley.sync360.service.SyncForegroundService
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.ContentValues
import android.provider.MediaStore
import org.koin.android.ext.android.inject

/**
 * Main entry-point activity for the Sync360 Android app.
 *
 * Responsibilities added in Phase 2:
 * - Requests POST_NOTIFICATIONS permission at runtime on Android 13+.
 * - Checks (and prompts for) SYSTEM_ALERT_WINDOW overlay permission.
 * - Starts / stops [SyncForegroundService] for background WebSocket sync.
 * - Shows / hides the [FloatingDockManager] floating bubble overlay.
 * - Passes service & overlay callbacks down to the shared [App] composable.
 */
class MainActivity : ComponentActivity() {

    private val platformOps: com.liftley.sync360.core.platform.PlatformOperations by inject()
    private val syncClient: com.liftley.sync360.core.network.SyncClient by inject()

    // ── Floating overlay manager (lazy-init after permissions are confirmed) ──
    private var floatingDockManager: FloatingDockManager? = null
    private var onFilePickedCallback: ((name: String, mimeType: String, content: ByteArray) -> Unit)? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            handleFilePicked(uri)
        }

    private val pickVisualMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            handleFilePicked(uri)
        }

    private fun handleFilePicked(uri: Uri?) {
        val callback = onFilePickedCallback ?: return
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = applicationContext.contentResolver
                    var fileName = "file_${System.currentTimeMillis()}"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                            if (sizeIndex != -1) {
                                val size = cursor.getLong(sizeIndex)
                                if (size > 50 * 1024 * 1024) {
                                    launch(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "File exceeds 50MB limit", Toast.LENGTH_LONG).show()
                                    }
                                    return@launch
                                }
                            }
                        }
                    }
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        callback(fileName, mimeType, bytes)
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    onFilePickedCallback = null
                }
            }
        } else {
            onFilePickedCallback = null
        }
    }

    private fun saveFileToDownloads(name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = applicationContext.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Sync360")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { it.write(content) }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Saved: $name to Downloads", Toast.LENGTH_LONG).show()
                            onResult(true, uri.toString())
                        }
                        return@launch
                    }
                }
                
                // Fallback for pre-Q or if MediaStore fails
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val syncDir = java.io.File(downloadsDir, "Sync360")
                if (!syncDir.exists()) syncDir.mkdirs()
                val file = java.io.File(syncDir, name)
                file.writeBytes(content)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Saved: ${file.name} to Downloads", Toast.LENGTH_LONG).show()
                    onResult(true, file.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    val extDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(extDir, name)
                    file.writeBytes(content)
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Saved to App Storage (Downloads folder blocked)", Toast.LENGTH_LONG).show()
                        onResult(true, file.absolutePath)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Failed to save: ${ex.message}", Toast.LENGTH_SHORT).show()
                        onResult(false, null)
                    }
                }
            }
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Permission accepted — no further action needed.
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied — background sync status won't show.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                initFloatingDock()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission denied — floating dock unavailable.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+.
        requestNotificationPermissionIfNeeded()

        // Initialise the floating dock if overlay permission is already granted.
        if (Settings.canDrawOverlays(this)) {
            initFloatingDock()
        }

        // Bind callbacks to the injected AndroidPlatformOperations
        val androidOps = platformOps as? com.liftley.sync360.core.platform.AndroidPlatformOperations
        if (androidOps != null) {
            androidOps.onShowOverlayCallback = { showOverlay() }
            androidOps.onHideOverlayCallback = { hideOverlay() }
            androidOps.onOpenFilePickerCallback = { kind, callback ->
                onFilePickedCallback = callback
                when (kind) {
                    com.liftley.sync360.features.sync.presentation.SyncEvent.FilePickerKind.Media -> {
                        pickVisualMediaLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                    com.liftley.sync360.features.sync.presentation.SyncEvent.FilePickerKind.Any -> {
                        filePickerLauncher.launch("*/*")
                    }
                }
            }
            androidOps.onOpenFileCallback = { path ->
                try {
                    val uri = if (path.startsWith("content://")) {
                        Uri.parse(path)
                    } else {
                        androidx.core.content.FileProvider.getUriForFile(
                            this@MainActivity,
                            "$packageName.provider",
                            java.io.File(path)
                        )
                    }
                    val mimeType = applicationContext.contentResolver.getType(uri) ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        val uri = Uri.parse(path)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "*/*")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        Toast.makeText(this@MainActivity, "Could not open file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            androidOps.onSaveFileCallback = { name, bytes, onResult ->
                saveFileToDownloads(name, bytes, onResult)
            }
        }

        setContent {
            App(isDesktop = false)
        }
    }

    override fun onDestroy() {
        floatingDockManager?.hide()
        syncClient.close()
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun initFloatingDock() {
        if (floatingDockManager != null) return

        val sharedPrefs = getSharedPreferences("sync360_prefs", MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("device_uuid", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            sharedPrefs.edit { putString("device_uuid", deviceId) }
        }

        floatingDockManager = FloatingDockManager(
            context = applicationContext,
            onClipboardSend = { clipText ->
                val payload = SyncPayload(
                    kind = "clipboard",
                    originDeviceId = "android-$deviceId",
                    originDeviceName = listOf(Build.MANUFACTURER, Build.MODEL)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Android device" },
                    originDeviceType = DeviceType.PHONE.name,
                    content = clipText,
                    timestamp = System.currentTimeMillis()
                )
                SyncForegroundService.syncClient?.sendFrame(SyncPayloadCodec.encode(payload))
            }
        )
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermissionIfNeeded()
            return
        }
        initFloatingDock()
        floatingDockManager?.show()
    }

    private fun hideOverlay() {
        floatingDockManager?.hide()
    }
}
