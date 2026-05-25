package com.liftley.sync360.core.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.BufferedOutputStream
import java.io.OutputStream

class AndroidPlatformOperations(private val context: Context) : PlatformOperations {
    
    var onShowOverlayCallback: (() -> Unit)? = null
    var onHideOverlayCallback: (() -> Unit)? = null
    var onOpenFilePickerCallback: ((kind: FilePickerKind, onFilesSelected: (files: List<PickedFile>) -> Unit) -> Unit)? = null
    var onOpenFileCallback: ((path: String) -> Unit)? = null
    var onSaveFileCallback: ((name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit) -> Unit)? = null
    var onSaveFileChunksCallback: ((name: String, chunks: List<ByteArray>, onResult: (success: Boolean, path: String?) -> Unit) -> Unit)? = null
    private val activeFileWrites = mutableMapOf<String, AndroidFileWrite>()

    override fun startService(hostIp: String) {
        try {
            val intent = Intent().apply {
                setClassName(context.packageName, "com.liftley.sync360.service.SyncService")
            }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            println("AndroidPlatformOperations: Failed to start SyncService - ${e.message}")
        }
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

    override fun showOverlay() {
        onShowOverlayCallback?.invoke()
    }

    override fun hideOverlay() {
        onHideOverlayCallback?.invoke()
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
        onOpenFilePickerCallback?.invoke(kind, onFilesSelected)
    }

    override suspend fun readFileChunks(
        file: PickedFile,
        chunkSizeBytes: Int,
        onChunk: suspend (ByteArray) -> Unit
    ): Boolean {
        return try {
            val uri = android.net.Uri.parse(file.id)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(chunkSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    onChunk(buffer.copyOf(read))
                }
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun saveFile(
        name: String,
        content: ByteArray,
        onResult: (success: Boolean, path: String?) -> Unit
    ) {
        onSaveFileCallback?.invoke(name, content, onResult)
    }

    override fun saveFileChunks(
        name: String,
        chunks: List<ByteArray>,
        onResult: (success: Boolean, path: String?) -> Unit
    ) {
        onSaveFileChunksCallback?.invoke(name, chunks, onResult)
            ?: onResult(false, null)
    }

    override fun beginFileWrite(name: String): String? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/Sync360"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val output = resolver.openOutputStream(uri)?.let(::BufferedOutputStream)
            if (output == null) {
                resolver.delete(uri, null, null)
                return null
            }
            val handle = uri.toString()
            synchronized(activeFileWrites) {
                activeFileWrites[handle] = AndroidFileWrite(uri, output)
            }
            handle
        } catch (_: Exception) {
            null
        }
    }

    override fun writeFileChunk(handle: String, bytes: ByteArray): Boolean {
        return try {
            val output = synchronized(activeFileWrites) { activeFileWrites[handle]?.output } ?: return false
            output.write(bytes)
            output.flush()
            true
        } catch (e: Exception) {
            println("AndroidPlatformOperations: writeFileChunk failed - ${e.message}")
            cancelFileWrite(handle)
            false
        }
    }

    override fun finishFileWrite(handle: String): String? {
        return try {
            val write = synchronized(activeFileWrites) { activeFileWrites.remove(handle) } ?: return null
            write.output.flush()
            write.output.close()
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(write.uri, values, null, null)
            write.uri.toString()
        } catch (_: Exception) {
            cancelFileWrite(handle)
            null
        }
    }

    override fun cancelFileWrite(handle: String) {
        val write = synchronized(activeFileWrites) { activeFileWrites.remove(handle) } ?: return
        try {
            write.output.close()
        } catch (_: Exception) {
        }
        runCatching {
            context.contentResolver.delete(write.uri, null, null)
        }
    }

    override fun openFile(path: String) {
        onOpenFileCallback?.invoke(path)
    }

    // Android is mostly a client, so Server operations are largely no-ops if not supported
    override fun startServer(port: Int) {}
    
    override fun stopServer() {}
    
    override fun broadcastToServer(text: String) {}
    
    override fun disconnectServerClient(deviceId: String) {}

    override fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val host = address.hostAddress ?: continue
                    if (!address.isLoopbackAddress && host.contains('.')) {
                        return host
                    }
                }
            }
            "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    override fun getIncomingMessagesFlow(): Flow<String> {
        return emptyFlow()
    }
}

private data class AndroidFileWrite(
    val uri: Uri,
    val output: OutputStream
)
