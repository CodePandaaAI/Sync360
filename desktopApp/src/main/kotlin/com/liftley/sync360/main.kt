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
            onWriteClipboard = { text -> writeDesktopClipboardText(text) }
        )
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