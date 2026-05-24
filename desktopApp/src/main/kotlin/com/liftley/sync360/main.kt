package com.liftley.sync360

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.liftley.sync360.network.NetworkUtils
import com.liftley.sync360.network.SyncServer

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

fun main() = application {
    val syncServer = remember {
        SyncServer().apply {
            start(8080)
        }
    }
    
    val clientCount by syncServer.activeClientCount.collectAsState()
    val localIp = remember { NetworkUtils.getLocalIpAddress() }

    Window(
        onCloseRequest = {
            syncServer.stop()
            exitApplication()
        },
        title = "Sync360 Desktop Console",
    ) {
        App(
            isDesktop = true,
            serverIp = localIp,
            serverClientCount = clientCount,
            serverIncomingFlow = syncServer.incomingMessages,
            onServerBroadcast = { text ->
                syncServer.broadcast(text)
            },
            onReadClipboard = { readDesktopClipboardText() },
            onWriteClipboard = { text -> writeDesktopClipboardText(text) },
            onOpenFilePicker = { mimeType, callback ->
                openDesktopFilePicker(mimeType, callback)
            },
            onSaveFile = { name, bytes, onResult ->
                saveDesktopFile(name, bytes, onResult)
            }
        )
    }
}

private fun openDesktopFilePicker(mimeType: String, onFileSelected: (name: String, content: ByteArray) -> Unit) {
    try {
        val mode = java.awt.FileDialog.LOAD
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select File to Share", mode)
        dialog.isVisible = true
        val file = dialog.file
        val directory = dialog.directory
        if (file != null && directory != null) {
            val selectedFile = java.io.File(directory, file)
            val bytes = selectedFile.readBytes()
            onFileSelected(file, bytes)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun saveDesktopFile(name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit) {
    try {
        val downloadsPath = System.getProperty("user.home") + java.io.File.separator + "Downloads" + java.io.File.separator + "Sync360"
        val dir = java.io.File(downloadsPath)
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, name)
        file.writeBytes(content)
        onResult(true, file.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(false, null)
    }
}

private fun readDesktopClipboardText(): String? {

    return try {
        val transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
        if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            transferable.getTransferData(DataFlavor.stringFlavor) as String
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun writeDesktopClipboardText(text: String) {
    try {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}