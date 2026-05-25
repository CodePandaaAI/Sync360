package com.liftley.sync360

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin
import com.liftley.sync360.network.SyncServer

fun main() {
    val koinApp = startKoin {
        modules(com.liftley.sync360.core.di.platformModule, com.liftley.sync360.core.di.commonModule)
    }
    
    val server: SyncServer = koinApp.koin.get()

    application {
        Window(
            onCloseRequest = {
                server.stop()
                exitApplication()
            },
            title = "Sync360 Desktop Console",
        ) {
            App(isDesktop = true)
        }
    }
}