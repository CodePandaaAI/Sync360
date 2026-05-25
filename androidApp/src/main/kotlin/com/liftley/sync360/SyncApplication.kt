package com.liftley.sync360

import android.app.Application
import com.liftley.sync360.core.di.AppContainer

class SyncApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(context = this, isDesktopPlatform = false)
    }
}
