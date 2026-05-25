package com.liftley.sync360.core.platform

import kotlinx.coroutines.flow.Flow

interface PlatformOperations {
    // Client & General system operations
    fun startService(hostIp: String)
    fun stopService()
    fun showOverlay()
    fun hideOverlay()
    fun readClipboard(): String?
    fun writeClipboard(text: String)
    fun openFilePicker(kind: FilePickerKind, onFilesSelected: (files: List<com.liftley.sync360.features.sync.domain.model.PickedFile>) -> Unit)
    suspend fun readFileChunks(file: com.liftley.sync360.features.sync.domain.model.PickedFile, chunkSizeBytes: Int, onChunk: suspend (ByteArray) -> Unit): Boolean
    fun saveFile(name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit)
    fun saveFileChunks(name: String, chunks: List<ByteArray>, onResult: (success: Boolean, path: String?) -> Unit)
    fun beginFileWrite(name: String): String?
    fun writeFileChunk(handle: String, bytes: ByteArray): Boolean
    fun finishFileWrite(handle: String): String?
    fun cancelFileWrite(handle: String)
    fun openFile(path: String)

    // Server-specific operations (No-ops on mobile)
    fun startServer(port: Int = 8080)
    fun stopServer()
    fun broadcastToServer(text: String)
    fun disconnectServerClient(deviceId: String)
    fun getLocalIpAddress(): String
    fun getIncomingMessagesFlow(): Flow<String>?
}
