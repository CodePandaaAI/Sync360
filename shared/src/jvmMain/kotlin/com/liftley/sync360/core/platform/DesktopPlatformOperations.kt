package com.liftley.sync360.core.platform

import com.liftley.sync360.features.sync.domain.model.PickedFile
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface

class DesktopPlatformOperations : PlatformOperations {
    private val activeFileWrites = mutableMapOf<String, DesktopFileWrite>()

    override fun startTransferService(): BackgroundServiceStartResult =
        BackgroundServiceStartResult.NOT_REQUIRED
    override fun stopService() {}

    override fun readClipboard(): String? {
        var attempts = 3
        while (attempts > 0) {
            try {
                val transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
                if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    return transferable.getTransferData(DataFlavor.stringFlavor) as String
                }
                return null
            } catch (e: Exception) {
                attempts--
                if (attempts > 0) {
                    try { Thread.sleep(50) } catch (_: Exception) {}
                }
            }
        }
        return null
    }

    override fun writeClipboard(text: String) {
        var attempts = 3
        while (attempts > 0) {
            try {
                val selection = StringSelection(text)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                return
            } catch (e: Exception) {
                attempts--
                if (attempts > 0) {
                    try { Thread.sleep(50) } catch (_: Exception) {}
                } else {
                    e.printStackTrace()
                }
            }
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
                        id = selectedFile.absolutePath,
                        name = selectedFile.name,
                        mimeType = java.nio.file.Files.probeContentType(selectedFile.toPath()) ?: "application/octet-stream",
                        sizeBytes = selectedFile.length()
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

    override suspend fun readFileChunks(
        file: PickedFile,
        chunkSizeBytes: Int,
        onChunk: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ): FileOperationResult<Long> = withContext(Dispatchers.IO) {
        try {
            var bytesRead = 0L
            File(file.id).inputStream().use { input ->
                val buffer = ByteArray(chunkSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    bytesRead += read
                    onChunk(buffer, 0, read)
                }
            }
            FileOperationResult.Success(bytesRead)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            FileOperationResult.Failure(PlatformFileError.READ_FAILED)
        }
    }

    override fun beginFileWrite(name: String): FileOperationResult<String> {
        return try {
            val userHome = System.getProperty("user.home")
            val downloadsDir = File(userHome, "Downloads")
            var syncDir = File(downloadsDir, "Sync360")
            if ((!syncDir.exists() && !syncDir.mkdirs()) || !syncDir.isDirectory || !syncDir.canWrite()) {
                syncDir = File(userHome, "Sync360")
                if ((!syncDir.exists() && !syncDir.mkdirs()) || !syncDir.isDirectory || !syncDir.canWrite()) {
                    return FileOperationResult.Failure(PlatformFileError.DESTINATION_UNAVAILABLE)
                }
            }
            val selectedFile = uniqueFile(syncDir, name)
            val output = selectedFile.outputStream().buffered()
            val handle = selectedFile.absolutePath
            synchronized(activeFileWrites) {
                activeFileWrites[handle] = DesktopFileWrite(selectedFile, output)
            }
            FileOperationResult.Success(handle)
        } catch (error: Exception) {
            FileOperationResult.Failure(fileError(error, PlatformFileError.DESTINATION_UNAVAILABLE))
        }
    }

    override fun getAvailableStorageBytes(): FileOperationResult<Long> {
        return try {
            val userHome = System.getProperty("user.home")
            val downloadsDir = File(userHome, "Downloads")
            val storageRoot = downloadsDir.takeIf { it.exists() } ?: File(userHome)
            val available = storageRoot.usableSpace
            FileOperationResult.Success(available)
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
            FileOperationResult.Success(write.file.absolutePath)
        } catch (error: Exception) {
            runCatching { write.output.close() }
            runCatching { write.file.delete() }
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
        if (!write.file.delete() && write.file.exists()) failed = true
        return if (failed) {
            FileOperationResult.Failure(PlatformFileError.CANCEL_FAILED)
        } else {
            FileOperationResult.Success(Unit)
        }
    }

    override fun deleteFile(path: String): FileOperationResult<Unit> {
        return try {
            val file = File(path)
            if (!file.exists() || file.delete()) {
                FileOperationResult.Success(Unit)
            } else {
                FileOperationResult.Failure(PlatformFileError.DELETE_FAILED)
            }
        } catch (_: Exception) {
            FileOperationResult.Failure(PlatformFileError.DELETE_FAILED)
        }
    }

    override fun openFile(path: String): FileOperationResult<Unit> {
        return try {
            val file = File(path)
            if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file)
                FileOperationResult.Success(Unit)
            } else {
                FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
        }
    }

    override fun showFileInFolder(path: String): FileOperationResult<Unit> {
        return try {
            val file = File(path)
            if (file.exists() && java.awt.Desktop.isDesktopSupported()) {
                val osName = System.getProperty("os.name").lowercase()
                if (osName.contains("win")) {
                    Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
                } else if (osName.contains("mac")) {
                    Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
                } else {
                    java.awt.Desktop.getDesktop().open(file.parentFile)
                }
                FileOperationResult.Success(Unit)
            } else {
                FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
        }
    }

    override fun openDownloadsFolder(): FileOperationResult<Unit> {
        return try {
            val downloadsDir = File(System.getProperty("user.home"), "Downloads")
            if (downloadsDir.exists() && java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(downloadsDir)
                FileOperationResult.Success(Unit)
            } else {
                FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FileOperationResult.Failure(PlatformFileError.OPEN_FAILED)
        }
    }

    override fun getNetworkEnvironment(): NetworkEnvironment {
        val addresses = try {
            buildList {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) continue
                    val kind = networkInterfaceKind(
                        networkInterface.name,
                        networkInterface.displayName.orEmpty()
                    )

                    val interfaceAddresses = networkInterface.inetAddresses
                    while (interfaceAddresses.hasMoreElements()) {
                        val address = interfaceAddresses.nextElement()
                        if (
                            !address.isLoopbackAddress &&
                            !address.isLinkLocalAddress &&
                            address is Inet4Address
                        ) {
                            add(LocalNetworkAddress(address.hostAddress, networkInterface.name, kind))
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

    private fun uniqueFile(directory: File, fileName: String): File {
        val cleanName = safeReceivedFileName(fileName)
        val dotIndex = cleanName.lastIndexOf('.').takeIf { it > 0 }
        val baseName = dotIndex?.let { cleanName.substring(0, it) } ?: cleanName
        val extension = dotIndex?.let { cleanName.substring(it) }.orEmpty()

        var candidate = File(directory, cleanName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$baseName ($index)$extension")
            index += 1
        }
        return candidate
    }

    private fun safeReceivedFileName(name: String): String {
        val sanitized = name
            .replace('\\', '_')
            .replace('/', '_')
            .filterNot { it.isISOControl() }
            .trim()
            .trim('.')
            .take(180)
        return sanitized.ifBlank { "received_file" }
    }

    private fun networkInterfaceKind(name: String, displayName: String): NetworkInterfaceKind {
        val value = "$name $displayName".lowercase()
        return when {
            listOf("vpn", "tun", "tap", "wireguard", "zerotier").any { it in value } ->
                NetworkInterfaceKind.VPN
            listOf("wi-fi", "wifi", "wlan", "wireless").any { it in value } ->
                NetworkInterfaceKind.WIFI
            listOf("ethernet", "eth", "en").any { it in value } ->
                NetworkInterfaceKind.ETHERNET
            listOf("mobile hotspot", "softap").any { it in value } ->
                NetworkInterfaceKind.HOTSPOT
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
}

private data class DesktopFileWrite(
    val file: File,
    val output: OutputStream
)
