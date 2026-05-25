package com.liftley.sync360.core.platform

import com.liftley.sync360.features.sync.presentation.SyncEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

class DesktopPlatformOperations : PlatformOperations {

    override fun startService(hostIp: String) {}
    override fun stopService() {}
    override fun showOverlay() {}
    override fun hideOverlay() {}

    override fun readClipboard(): String? {
        return try {
            val transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                transferable.getTransferData(DataFlavor.stringFlavor) as String
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override fun writeClipboard(text: String) {
        try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun openFilePicker(
        kind: SyncEvent.FilePickerKind,
        onFileSelected: (name: String, mimeType: String, content: ByteArray) -> Unit
    ) {
        try {
            val mode = java.awt.FileDialog.LOAD
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select File to Share", mode)
            dialog.isVisible = true
            val file = dialog.file
            val directory = dialog.directory
            if (file != null && directory != null) {
                val selectedFile = File(directory, file)
                val bytes = selectedFile.readBytes()
                val mimeType = java.nio.file.Files.probeContentType(selectedFile.toPath()) ?: "application/octet-stream"
                onFileSelected(file, mimeType, bytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun saveFile(
        name: String,
        content: ByteArray,
        onResult: (success: Boolean, path: String?) -> Unit
    ) {
        try {
            val downloadsDir = File(System.getProperty("user.home"), "Downloads")
            val syncDir = File(downloadsDir, "Sync360")
            syncDir.mkdirs()
            val selectedFile = File(syncDir, name)
            selectedFile.writeBytes(content)
            onResult(true, selectedFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(false, null)
        }
    }

    override fun openFile(path: String) {
        try {
            val file = File(path)
            if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // No-op for Server operations since we use Ktor CIO Network service
    override fun startServer(port: Int) {}
    override fun stopServer() {}
    override fun broadcastToServer(text: String) {}
    override fun disconnectServerClient(deviceId: String) {}

    override fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return try {
            InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    override fun getIncomingMessagesFlow(): Flow<String>? {
        return emptyFlow()
    }
}