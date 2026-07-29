package com.liftley.sync360

import androidx.compose.ui.window.ComposeUIViewController
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.core.di.initKoinSync360
import com.liftley.sync360.core.di.iosModule
import com.liftley.sync360.core.platform.IosViewControllerProvider
import com.liftley.sync360.data.NetworkServicesController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

private val iosKoinApplication by lazy {
    initKoinSync360(iosModule) {}
        .also { koinApplication ->
            koinApplication.koin
                .get<NetworkServicesController>()
                .startNetworkServices()
        }
}

@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController {
    val koinApplication = iosKoinApplication
    val composeViewController = ComposeUIViewController {
        Sync360Theme {
            Sync360Root()
        }
    }

    koinApplication.koin
        .get<IosViewControllerProvider>()
        .attach(composeViewController)

    return composeViewController
}
