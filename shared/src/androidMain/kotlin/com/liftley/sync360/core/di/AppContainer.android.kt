package com.liftley.sync360.core.di

import android.content.Context
import com.liftley.sync360.core.platform.AndroidPlatformOperations
import com.liftley.sync360.core.platform.PlatformOperations

actual fun createPlatformOperations(context: Any?): PlatformOperations {
    val androidContext = context as? Context
        ?: throw IllegalArgumentException("Android Context required")
    return AndroidPlatformOperations(androidContext)
}
