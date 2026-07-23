package com.liftley.sync360

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.core.di.initKoin
import com.liftley.sync360.core.di.jvmModule
import org.jetbrains.compose.resources.painterResource
import sync360.shared.generated.resources.Res
import sync360.shared.generated.resources.app_icon

fun main() {
    initKoin(jvmModule) {}

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sync360",
            icon = painterResource(Res.drawable.app_icon)
        ) {
            Sync360Theme(darkTheme = false, dynamicColor = false) {
                Sync360Root()
            }
        }
    }
}
