package com.liftley.sync360

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liftley.sync360.core.designsystem.Sync360Theme
import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.presentation.PermissionScreen
import org.koin.android.ext.android.inject

const val ACCESS_LOCAL_NETWORK_PERMISSION =
    "android.permission.ACCESS_LOCAL_NETWORK"

fun Context.hasLocalNetworkAccess(): Boolean {
    return Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission(ACCESS_LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
}

class MainActivity : ComponentActivity() {

    private val networkServicesController: NetworkServicesController by inject()

    private var isPermissionGranted by mutableStateOf(false)

    private val localNetworkPermissionRequest =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            isPermissionGranted = granted

            if (granted) {
                networkServicesController.startNetworkServices()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        isPermissionGranted = hasLocalNetworkAccess()

        setContent {
            Sync360Theme {
                if (isPermissionGranted) {
                    Sync360Root()
                } else {
                    PermissionScreen {
                        localNetworkPermissionRequest.launch(
                            ACCESS_LOCAL_NETWORK_PERMISSION
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val currentlyGranted = hasLocalNetworkAccess()
        if (currentlyGranted == isPermissionGranted) return

        isPermissionGranted = currentlyGranted

        if (currentlyGranted) {
            networkServicesController.startNetworkServices()
        } else {
            networkServicesController.stopNetworkServices()
        }
    }
}