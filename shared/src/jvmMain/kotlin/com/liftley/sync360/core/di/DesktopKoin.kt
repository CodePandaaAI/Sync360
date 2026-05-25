package com.liftley.sync360.core.di

import org.koin.core.module.Module
import org.koin.dsl.module
import com.liftley.sync360.core.database.DatabaseDriverFactory
import com.liftley.sync360.core.database.createDatabaseDriverFactory
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.DesktopPlatformOperations

actual val platformModule: Module = module {
    single<DatabaseDriverFactory> { createDatabaseDriverFactory(null) }
    single<PlatformOperations> { DesktopPlatformOperations() }
}
