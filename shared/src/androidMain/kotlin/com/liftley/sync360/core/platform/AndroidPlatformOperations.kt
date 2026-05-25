package com.liftley.sync360.core.platform

import android.content.Context
import android.content.Intent
import com.liftley.sync360.core.platform.FilePickerKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class AndroidPlatformOperations(private val context: Context) : PlatformOperations {
    
    var onShowOverlayCallback: (() -> Unit)? = null
    var onHideOverlayCallback: (() -> Unit)? = null
    var onOpenFilePickerCallback: ((kind: FilePickerKind, onFileSelected: (name: String, mimeType: String, content: ByteArray) -> Unit) -> Unit)? = null
    var onOpenFileCallback: ((path: String) -> Unit)? = null
    var onSaveFileCallback: ((name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit) -> Unit)? = null

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
        onFileSelected: (name: String, mimeType: String, content: ByteArray) -> Unit
    ) {
        onOpenFilePickerCallback?.invoke(kind, onFileSelected)
    }

    override fun saveFile(
        name: String,
        content: ByteArray,
        onResult: (success: Boolean, path: String?) -> Unit
    ) {
        onSaveFileCallback?.invoke(name, content, onResult)
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