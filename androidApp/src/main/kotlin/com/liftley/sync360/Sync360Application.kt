package com.liftley.sync360

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    networkServices.startNetworkServices()
                    Log.d("LC", "visible")
                }

                override fun onStop(owner: LifecycleOwner) {
                    networkServices.stopNetworkServices()
                    Log.d("LC", "hidden")
                }
            }
        )
    }
}
