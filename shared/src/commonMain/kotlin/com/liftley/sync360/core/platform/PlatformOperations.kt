package com.liftley.sync360.core.platform

import com.liftley.sync360.features.sync.presentation.SyncEvent
import kotlinx.coroutines.flow.Flow

interface PlatformOperations {
    // Client & General system operations
    fun startService(hostIp: String)
    fun stopService()
    fun showOverlay()
    fun hideOverlay()
    fun readClipboard(): String?
    fun writeClipboard(text: String)
    fun openFilePicker(kind: SyncEvent.FilePickerKind, onFileSelected: (name: String, mimeType: String, content: ByteArray) -> Unit)
    fun saveFile(name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit)
    fun openFile(path: String)

    // Server-specific operations (No-ops on mobile)
    fun startServer(port: Int = 8080)
    fun stopServer()
    fun broadcastToServer(text: String)
    fun disconnectServerClient(deviceId: String)
    fun getLocalIpAddress(): String
    fun getIncomingMessagesFlow(): Flow<String>?
}
