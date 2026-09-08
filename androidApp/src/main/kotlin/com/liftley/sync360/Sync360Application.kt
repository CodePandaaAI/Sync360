package com.liftley.sync360

import android.app.Application
import com.liftley.sync360.core.di.androidModule
import com.liftley.sync360.core.di.initKoinSync360
import com.liftley.sync360.data.NetworkServicesController
import org.koin.android.ext.koin.androidContext

class Sync360Application : Application() {
    override fun onCreate() {
        super.onCreate()
        val koinApplication = initKoinSync360(androidModule) {
            androidContext(applicationContext)
        }

        val networkServices = koinApplication.koin.get<NetworkServicesController>()
        networkServices.startNetworkServices(discoveryAllowedAtStartup = false)
        AndroidNearbyDiscoveryObserver(networkServices).observeAppVisibility()
    }
}
