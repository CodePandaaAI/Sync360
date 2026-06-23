package com.liftley.sync360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liftley.sync360.core.di.androidModule
import com.liftley.sync360.core.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        startKoin {
            androidContext(applicationContext)
            modules(androidModule)
        }
        setContent {
            App()
        }
    }
}
