package com.liftley.sync360

import android.app.Application
import com.liftley.sync360.core.di.initKoin
import org.koin.android.ext.koin.androidContext

class SyncApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SyncApplication)
        }
    }
}
