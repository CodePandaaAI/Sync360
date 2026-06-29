package com.liftley.sync360

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.painterResource
import sync360.shared.generated.resources.Res
import sync360.shared.generated.resources.app_icon

fun main() {
    application {
        Window(
            onCloseRequest = {},
            title = "Sync360",
            icon = painterResource(Res.drawable.app_icon)
        ) {
        }
    }
}
