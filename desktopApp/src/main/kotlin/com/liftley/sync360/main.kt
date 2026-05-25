package com.liftley.sync360

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.koin.core.context.startKoin
import com.liftley.sync360.features.sync.domain.repository.SyncRepository

fun main() {
    val koinApp = startKoin {
        modules(com.liftley.sync360.core.di.platformModule, com.liftley.sync360.core.di.commonModule)
    }
    
    val repository: SyncRepository = koinApp.koin.get()

    application {
        Window(
            onCloseRequest = {
                repository.disconnectAll()
                exitApplication()
            },
            title = "Sync360 Desktop Console",
        ) {
            App(isDesktop = true)
        }
    }
}