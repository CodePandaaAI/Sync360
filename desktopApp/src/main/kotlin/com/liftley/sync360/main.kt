package com.liftley.sync360

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.core.di.appModule
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin
import sync360.shared.generated.resources.Res
import sync360.shared.generated.resources.app_icon

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Sync360",
            icon = painterResource(Res.drawable.app_icon)
        ) {
            startKoin {
                modules(appModule)
            }
            Sync360Root()
        }
    }
}
