package com.liftley.sync360.core.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.OsConstants
import androidx.core.app.NotificationManagerCompat
import com.liftley.sync360.features.sync.domain.model.PickedFile
import java.io.BufferedOutputStream
import java.io.OutputStream
import androidx.core.net.toUri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException

class AndroidPlatformOperations(private val context: Context) : PlatformOperations {

    @Volatile
    private var activityBridge: AndroidActivityBridge? = null
    private val activeFileWrites = mutableMapOf<String, AndroidFileWrite>()

    fun attachActivityBridge(bridge: AndroidActivityBridge) {
        activityBridge = bridge
    }

    fun detachActivityBridge(bridge: AndroidActivityBridge) {
        if (activityBridge === bridge) {
            activityBridge = null
        }
    }

    override fun startForegroundService(status: SyncForegroundServiceStatus): BackgroundServiceStartResult {
        return try {
            val intent = serviceIntent(status)
            context.startForegroundService(intent)
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                BackgroundServiceStartResult.STARTED
            } else {
                BackgroundServiceStartResult.STARTED_WITH_NOTIFICATION_BLOCKED
            }
        } catch (e: Exception) {
            println("AndroidPlatformOperations: Failed to start SyncService - ${e.message}")
            BackgroundServiceStartResult.FAILED
        }
    }

    override fun updateForegroundService(status: SyncForegroundServiceStatus) {
        startForegroundService(status)
    }

    override fun stopService() {
        try {
            val intent = Intent().apply {
                setClassName(context.packageName, "com.liftley.sync360.service.SyncService")
            }
            context.stopService(intent)
        } catch (e: Exception) {
            println("AndroidPlatformOperations: Failed to stop SyncService - ${e.message}")
        }
    }

    private fun serviceIntent(status: SyncForegroundServiceStatus): Intent {
        return Intent().apply {
            setClassName(context.packageName, "com.liftley.sync360.service.SyncService")
            putExtra(EXTRA_MODE, status.mode.toServiceMode())
            status.peerName?.let { putExtra(EXTRA_PEER_NAME, it) }
            status.detail?.let { putExtra(EXTRA_DETAIL, it) }
            status.progressPercent?.let { putExtra(EXTRA_PROGRESS, it) }
            putExtra(EXTRA_FILE_COUNT, status.fileCount)
        }
    }

    private fun SyncForegroundServiceMode.toServiceMode(): String = when (this) {
        SyncForegroundServiceMode.READY -> MODE_READY
        SyncForegroundServiceMode.CONNECTED -> MODE_CONNECTED
        SyncForegroundServiceMode.TRANSFERRING -> MODE_TRANSFERRING
        SyncForegroundServiceMode.ERROR -> MODE_ERROR
    }

    override fun readClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        val description = clipboard.primaryClipDescription ?: return null
        if (!description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML)) return null
            
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    override fun writeClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun openFilePicker(
        kind: FilePickerKind,
        onFilesSelected: (files: List<PickedFile>) -> Unit
    ) {
        activityBridge?.openFilePicker(kind, onFilesSelected)
    }

    override suspend fun readFileChunks(
        file: PickedFile,
        chunkSizeBytes: Int,
        onChunk: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ): FileOperationResult<Long> = withContext(Dispatchers.IO) {
        try {
            var bytesRead = 0L
            val uri = file.id.toUri()
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(chunkSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    bytesRead += read
                    onChunk(buffer, 0, read)
                }
            } ?: return@withContext FileOperationResult.Failure(PlatformFileError.SOURCE_UNAVAILABLE)
            FileOperationResult.Success(bytesRead)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            FileOperationResult.Failure(PlatformFileError.READ_FAILED)
        }
    }

    override fun beginFileWrite(name: String): FileOperationResult<String> {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)
        }
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeReceivedFileName(name))
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/Sync360"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)
            val output = resolver.openOutputStream(uri)?.let { BufferedOutputStream(it) }
            if (output == null) {
                resolver.delete(uri, null, null)
                return FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)
            }
            val handle = uri.toString()
            synchronized(activeFileWrites) {
                activeFileWrites[handle] = AndroidFileWrite(uri, output)
            }
            FileOperationResult.Success(handle)
        } catch (error: Exception) {
            FileOperationResult.Failure(fileError(error, PlatformFileError.DESTINATION_UNAVAILABLE))
        }
    }

    override fun getAvailableStorageBytes(): FileOperationResult<Long> {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)
        }
        return try {
            val stats = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
            FileOperationResult.Success(stats.availableBytes)
        } catch (_: Exception) {
            FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)
        }
    }

    override fun writeFileChunk(handle: String, bytes: ByteArray, offset: Int, length: Int): FileOperationResult<Int> {
        return try {
            val output = synchronized(activeFileWrites) { activeFileWrites[handle]?.output }
                ?: return FileOperationResult.Failure(PlatformFileError.INVALID_HANDLE)
            output.write(bytes, offset, length)
            FileOperationResult.Success(length)
        } catch (error: Exception) {
            println("AndroidPlatformOperations: writeFileChunk failed - ${error.message}")
            cancelFileWrite(handle)
            FileOperationResult.Failure(fileError(error, PlatformFileError.WRITE_FAILED))
        }
    }

    override fun finishFileWrite(handle: String): FileOperationResult<String> {
        val write = synchronized(activeFileWrites) { activeFileWrites.remove(handle) }
            ?: return FileOperationResult.Failure(PlatformFileError.INVALID_HANDLE)
        return try {
            write.output.flush()
            write.output.close()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            val updated = context.contentResolver.update(write.uri, values, null, null)
            check(updated > 0) { "Could not finalize MediaStore file" }
            FileOperationResult.Success(write.uri.toString())
        } catch (error: Exception) {
            runCatching { write.output.close() }
            runCatching { context.contentResolver.delete(write.uri, null, null) }
            FileOperationResult.Failure(fileError(error, PlatformFileError.FINALIZE_FAILED))
        }
    }

    override fun cancelFileWrite(handle: String): FileOperationResult<Unit> {
        val write = synchronized(activeFileWrites) { activeFileWrites.remove(handle) }
            ?: return FileOperationResult.Failure(PlatformFileError.INVALID_HANDLE)
        var failed = false
        try {
            write.output.close()
        } catch (_: Exception) {
            failed = true
        }
        if (runCatching {
            context.contentResolver.delete(write.uri, null, null)
        }.isFailure) failed = true
        return if (failed) {
            FileOperationResult.Failure(PlatformFileError.CANCEL_FAILED)
        } else {
            FileOperationResult.Success(Unit)
        }
    }

    override fun deleteFile(path: String): FileOperationResult<Unit> {
        return try {
            val uri = android.net.Uri.parse(path)
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                FileOperationResult.Success(Unit)
            } else {
                FileOperationResult.Failure(PlatformFileError.DELETE_FAILED)
            }
        } catch (_: Exception) {
            FileOperationResult.Failure(PlatformFileError.DELETE_FAILED)
        }
    }

    override fun openFile(path: String): FileOperationResult<Unit> {
        val bridge = activityBridge
            ?: return FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
        bridge.openFile(path)
        return FileOperationResult.Success(Unit)
    }

    override fun showFileInFolder(path: String): FileOperationResult<Unit> {
        val bridge = activityBridge
            ?: return FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
        bridge.showFileInFolder(path)
        return FileOperationResult.Success(Unit)
    }

    override fun openDownloadsFolder(): FileOperationResult<Unit> {
        val bridge = activityBridge
            ?: return FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
        bridge.openDownloadsFolder()
        return FileOperationResult.Success(Unit)
    }

    override fun getNetworkEnvironment(): NetworkEnvironment {
        val addresses = try {
            buildList {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isUp || networkInterface.isLoopback) continue
                    val kind = networkInterfaceKind(
                        networkInterface.name,
                        networkInterface.displayName.orEmpty()
                    )
                    val interfaceAddresses = networkInterface.inetAddresses
                    while (interfaceAddresses.hasMoreElements()) {
                        val address = interfaceAddresses.nextElement()
                        val host = address.hostAddress ?: continue
                        if (
                            !address.isLoopbackAddress &&
                            !address.isLinkLocalAddress &&
                            host.contains('.') &&
                            ':' !in host
                        ) {
                            add(LocalNetworkAddress(host, networkInterface.name, kind))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return NetworkEnvironment(
            addresses
                .distinctBy { it.address }
                .sortedWith(
                    compareBy<LocalNetworkAddress> { networkAddressPriority(it) }
                        .thenBy { it.address }
                )
        )
    }

    private fun safeReceivedFileName(name: String): String {
        val sanitized = name
            .replace('\\', '_')
            .replace('/', '_')
            .filterNot { it.isISOControl() }
            .trim()
            .take(180)
        return sanitized.ifBlank { "received_file" }
    }

    private fun networkInterfaceKind(name: String, displayName: String): NetworkInterfaceKind {
        val value = "$name $displayName".lowercase()
        return when {
            listOf("tun", "vpn", "ppp", "wg").any { it in value } -> NetworkInterfaceKind.VPN
            listOf("ap", "softap", "swlan").any { it in value } -> NetworkInterfaceKind.HOTSPOT
            listOf("wlan", "wifi").any { it in value } -> NetworkInterfaceKind.WIFI
            listOf("eth", "en").any { it in value } -> NetworkInterfaceKind.ETHERNET
            else -> NetworkInterfaceKind.OTHER
        }
    }

    private fun networkAddressPriority(address: LocalNetworkAddress): Int =
        when (address.kind) {
            NetworkInterfaceKind.WIFI -> 0
            NetworkInterfaceKind.HOTSPOT -> 1
            NetworkInterfaceKind.ETHERNET -> 2
            NetworkInterfaceKind.OTHER -> 3
            NetworkInterfaceKind.VPN -> 4
        }

    private fun fileError(error: Throwable, fallback: PlatformFileError): PlatformFileError {
        var current: Throwable? = error
        while (current != null) {
            if (current is ErrnoException && current.errno == OsConstants.ENOSPC) {
                return PlatformFileError.STORAGE_FULL
            }
            val message = current.message.orEmpty().lowercase()
            if (
                "no space" in message ||
                "disk full" in message ||
                "not enough space" in message
            ) {
                return PlatformFileError.STORAGE_FULL
            }
            current = current.cause
        }
        return fallback
    }

    private companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_PEER_NAME = "peer_name"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_FILE_COUNT = "file_count"
        const val MODE_READY = "ready"
        const val MODE_CONNECTED = "connected"
        const val MODE_TRANSFERRING = "transferring"
        const val MODE_ERROR = "error"
    }
}

private data class AndroidFileWrite(
    val uri: Uri,
    val output: OutputStream
)
