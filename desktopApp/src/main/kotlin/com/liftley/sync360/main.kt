package com.liftley.sync360

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.liftley.sync360.core.di.AppContainer
import kotlinx.coroutines.runBlocking

fun main() {
    val container = AppContainer(context = null, isDesktopPlatform = true)

    application {
        Window(
            onCloseRequest = {
                runBlocking { container.onAppExit() }
                exitApplication()
            },
            title = "Sync360",
        ) {
            App(isDesktop = true, container = container)
        }
    }
}
