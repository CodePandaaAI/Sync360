package com.liftley.sync360.core.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module


fun initKoin(platformModule: Module) {
    startKoin {
        modules(platformModule)
    }
}