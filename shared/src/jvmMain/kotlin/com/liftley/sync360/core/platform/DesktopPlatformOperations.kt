package com.liftley.sync360.core.platform

import com.liftley.sync360.features.sync.domain.model.PickedFile
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
        kind: FilePickerKind,
        onFilesSelected: (files: List<PickedFile>) -> Unit
    ) {
        try {
            val mode = java.awt.FileDialog.LOAD
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select File to Share", mode)
            dialog.isMultipleMode = true
            dialog.isVisible = true
            val files = dialog.files.toList().ifEmpty {
                val file = dialog.file
                val directory = dialog.directory
                if (file != null && directory != null) listOf(File(directory, file)) else emptyList()
            }
            val picked = files.mapNotNull { selectedFile ->
                runCatching {
                    PickedFile(
                        name = selectedFile.name,
                        mimeType = java.nio.file.Files.probeContentType(selectedFile.toPath()) ?: "application/octet-stream",
                        content = selectedFile.readBytes()
                    )
                }.getOrNull()
            }
            if (picked.isNotEmpty()) {
                onFilesSelected(picked)
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
                
                // Filter out virtual interfaces (Hyper-V, WSL, VirtualBox, Docker, etc.)
                val name = networkInterface.name.lowercase()
                val displayName = networkInterface.displayName.lowercase()
                if (name.contains("virtual") || displayName.contains("virtual") ||
                    name.contains("hyper-v") || displayName.contains("hyper-v") ||
                    name.contains("host-only") || displayName.contains("host-only") ||
                    name.contains("wsl") || displayName.contains("wsl") ||
                    name.contains("vmware") || displayName.contains("vmware") ||
                    name.contains("vbox") || displayName.contains("vbox") ||
                    name.contains("vpn") || displayName.contains("vpn") ||
                    name.contains("virtualbox") || displayName.contains("virtualbox") ||
                    name.contains("zerotier") || displayName.contains("zerotier") ||
                    name.contains("docker") || displayName.contains("docker") ||
                    name.contains("vethernet") || displayName.contains("vethernet")) {
                    continue
                }

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
