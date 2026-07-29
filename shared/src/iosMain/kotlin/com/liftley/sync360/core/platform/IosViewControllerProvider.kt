package com.liftley.sync360.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

@OptIn(ExperimentalForeignApi::class)
class IosViewControllerProvider {
    var rootViewController: UIViewController? = null
        private set

    fun attach(rootViewController: UIViewController) {
        this.rootViewController = rootViewController
    }
}
