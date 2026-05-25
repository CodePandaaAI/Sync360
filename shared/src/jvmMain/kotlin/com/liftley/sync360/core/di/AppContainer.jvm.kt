package com.liftley.sync360.core.di

import com.liftley.sync360.core.platform.DesktopPlatformOperations
import com.liftley.sync360.core.platform.PlatformOperations

actual fun createPlatformOperations(context: Any?): PlatformOperations = DesktopPlatformOperations()
