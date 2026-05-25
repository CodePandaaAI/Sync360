package com.liftley.sync360

import android.app.Application
import com.liftley.sync360.core.di.commonModule
import com.liftley.sync360.core.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SyncApplication)
            modules(platformModule, commonModule)
        }
    }
}
