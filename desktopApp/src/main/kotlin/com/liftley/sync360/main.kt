package com.liftley.sync360

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.liftley.sync360.core.di.initKoin
import com.liftley.sync360.features.sync.domain.usecase.ClearAllDataUseCase
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.jetbrains.compose.resources.painterResource
import sync360.shared.generated.resources.Res
import sync360.shared.generated.resources.app_icon

fun main() {
    System.setProperty("java.net.preferIPv4Stack", "true")
    initKoin()

    application {
        Window(
            onCloseRequest = {
                runBlocking {
                    val koin = GlobalContext.get()
                    koin.get<DisconnectAllUseCase>().invoke()
                    koin.get<ClearAllDataUseCase>().invoke()
                }
                exitApplication()
            },
            title = "Sync360",
            icon = painterResource(Res.drawable.app_icon)
        ) {
            DesktopRoot()
        }
    }
}
