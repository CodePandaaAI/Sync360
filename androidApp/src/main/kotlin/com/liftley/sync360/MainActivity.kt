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

    // ── Floating overlay manager (lazy-init after permissions are confirmed) ──
    private var floatingDockManager: FloatingDockManager? = null
    private var onFilePickedCallback: ((name: String, content: ByteArray) -> Unit)? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val callback = onFilePickedCallback ?: return@registerForActivityResult
            if (uri != null) {
                try {
                    val contentResolver = applicationContext.contentResolver
                    var fileName = "file_${System.currentTimeMillis()}"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        callback(fileName, bytes)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun saveFileToDownloads(name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit) {
        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val syncDir = java.io.File(downloadsDir, "Sync360")
            if (!syncDir.exists()) syncDir.mkdirs()
            val file = java.io.File(syncDir, name)
            file.writeBytes(content)
            runOnUiThread {
                Toast.makeText(this, "Saved: ${file.name} to Downloads", Toast.LENGTH_LONG).show()
                onResult(true, file.absolutePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val extDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(extDir, name)
                file.writeBytes(content)
                runOnUiThread {
                    Toast.makeText(this, "Saved to App Storage (Downloads folder blocked)", Toast.LENGTH_LONG).show()
                    onResult(true, file.absolutePath)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to save: ${ex.message}", Toast.LENGTH_SHORT).show()
                    onResult(false, null)
                }
            }
        }
    }

    // ── Runtime permission launcher for POST_NOTIFICATIONS (Android 13+) ──

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

    // ── Overlay permission result callback ──
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

    // ──────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+.
        requestNotificationPermissionIfNeeded()

        // Initialise the floating dock if overlay permission is already granted.
        if (Settings.canDrawOverlays(this)) {
            initFloatingDock()
        }

        setContent {
            App(
                isDesktop = false,
                platformContext = applicationContext,
                onStartService = { hostIp -> startSyncService(hostIp) },
                onStopService = { stopSyncService() },
                onShowOverlay = { showOverlay() },
                onHideOverlay = { hideOverlay() },
                onReadClipboard = { readCurrentClipboardText() },
                onWriteClipboard = { text -> writeCurrentClipboardText(text) },
                onOpenFilePicker = { mimeType, callback ->
                    onFilePickedCallback = callback
                    filePickerLauncher.launch(mimeType)
                },
                onSaveFile = { name, bytes, onResult ->
                    saveFileToDownloads(name, bytes, onResult)
                }
            )
        }
    }


    private fun writeCurrentClipboardText(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("Sync360", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onDestroy() {
        // Clean up the floating overlay when the activity is destroyed.
        floatingDockManager?.hide()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────
    // Permission Helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * On Android 13 (TIRAMISU) and above, POST_NOTIFICATIONS is a runtime
     * permission. We request it here so the foreground service notification
     * can be displayed.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Checks whether the app has SYSTEM_ALERT_WINDOW (overlay) permission.
     * If not, redirects the user to the system settings page for this app.
     */
    private fun requestOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Foreground Service Controls
    // ──────────────────────────────────────────────────────────────────

    /**
     * Starts [SyncForegroundService] with the given [hostIp] passed via
     * Intent extras. On Android O+ uses `startForegroundService()`.
     */
    private fun startSyncService(hostIp: String) {
        val serviceIntent = Intent(this, SyncForegroundService::class.java).apply {
            putExtra(SyncForegroundService.EXTRA_HOST_IP, hostIp)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    /**
     * Stops [SyncForegroundService]. The service's `onDestroy()` will handle
     * WebSocket disconnection and cleanup.
     */
    private fun stopSyncService() {
        val serviceIntent = Intent(this, SyncForegroundService::class.java)
        stopService(serviceIntent)
    }

    // ──────────────────────────────────────────────────────────────────
    // Floating Dock Controls
    // ──────────────────────────────────────────────────────────────────

    /**
     * Lazily initialises the [FloatingDockManager]. The overlay sends the
     * clipboard text through the service's [SyncClient] when tapped.
     */
    private fun initFloatingDock() {
        if (floatingDockManager != null) return

        val sharedPrefs = getSharedPreferences("sync360_prefs", MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("device_uuid", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            sharedPrefs.edit {putString("device_uuid", deviceId)}
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

    /**
     * Shows the floating bubble overlay, requesting the overlay permission
     * first if it hasn't been granted yet.
     */
    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermissionIfNeeded()
            return
        }
        initFloatingDock()
        floatingDockManager?.show()
    }

    /** Hides the floating bubble overlay. */
    private fun hideOverlay() {
        floatingDockManager?.hide()
    }

    private fun readCurrentClipboardText(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
        ) {
            return null
        }
        return clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(isDesktop = false)
}
