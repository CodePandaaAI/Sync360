package com.liftley.sync360.core.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import com.liftley.sync360.core.database.DatabaseDriverFactory
import com.liftley.sync360.core.database.createDatabaseDriverFactory
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.AndroidPlatformOperations

actual val platformModule: Module = module {
    single<DatabaseDriverFactory> { createDatabaseDriverFactory(androidContext()) }
    single<Any> { androidContext() }
    single<PlatformOperations> { AndroidPlatformOperations(androidContext()) }
}
