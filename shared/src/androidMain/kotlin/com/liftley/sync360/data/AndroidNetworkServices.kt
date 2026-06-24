package com.liftley.sync360.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.repository.NearbyDevice
import com.liftley.sync360.domain.repository.NetworkServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AndroidNetworkServices(
    context: Context,
    androidLocalDeviceIdentityStore: LocalDeviceIdentityStore
) : NetworkServices {

    init {
        Log.d("Android Network Services", "Created!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    }

    private val _nearbyDevices: MutableStateFlow<List<NearbyDevice>> = MutableStateFlow(emptyList())

    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()
    val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    val serviceType = "_sync360._tcp."

    val deviceUuid = androidLocalDeviceIdentityStore.getOrCreateDeviceUuid()

    val serviceInfo = NsdServiceInfo().apply {
        serviceType = "_sync360._tcp."
        serviceName = "Sync360 Network Services"
        port = 8080

        setAttribute("deviceUuid", deviceUuid)
        setAttribute("deviceName", Build.MODEL ?: "Android Device")
        setAttribute("deviceType", "Android")
        setAttribute("protocolVersion", "1")
    }

    val resolveListener = createResolveListener()

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) {
            Log.d("AndroidNetworkServices", "onDiscoveryStarted: $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.d("AndroidNetworkServices", "onDiscoveryStopped: $serviceType")
        }

        @Suppress("NewApi", "DEPRECATION")
        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            Log.d("AndroidNetworkServices", "onServiceFound: $serviceInfo")
            serviceInfo?.let {
                when (resolveListener) {
                    is NsdManager.ResolveListener -> {
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    }

                    is NsdManager.ServiceInfoCallback -> {
                        nsdManager.registerServiceInfoCallback(
                            serviceInfo,
                            executor,
                            resolveListener
                        )
                    }
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            Log.d("AndroidNetworkServices", "onServiceLost: $serviceInfo")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onStopDiscoveryFailed: $serviceType, $errorCode")
        }
    }

    val registrationListener = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onRegistrationFailed: $serviceInfo, $errorCode")
        }

        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
            Log.d("AndroidNetworkServices", "onServiceRegistered: $serviceInfo")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            Log.d("AndroidNetworkServices", "onServiceUnregistered: $serviceInfo")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onUnregistrationFailed: $serviceInfo, $errorCode")
        }
    }

    override fun startNetworkServices() {
        Log.d("AndroidNetworkServices", "startNetworkServices: Starting discovery and registration")
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    override fun stopNetworkServices() {
        Log.d("AndroidNetworkServices", "stopNetworkServices: Stopping network services")
    }

    fun createResolveListener(): Any {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.d(
                        "AndroidNetworkServices",
                        "onServiceInfoCallbackRegistrationFailed: $errorCode"
                    )
                }

                override fun onServiceInfoCallbackUnregistered() {
                    Log.d("AndroidNetworkServices", "onServiceInfoCallbackUnregistered")
                }

                override fun onServiceLost() {
                    Log.d("AndroidNetworkServices", "onServiceLost")
                }

                override fun onServiceUpdated(resolvedDeviceInfo: NsdServiceInfo) {
                    Log.d("AndroidNetworkServices", "onServiceUpdated: $resolvedDeviceInfo")
                    _nearbyDevices.update { currentList ->
                        val newDevice = resolvedDeviceInfo.toNearbyDevice() ?: return@update currentList
                        val withoutOldDeviceId = currentList.filterNot { device -> device.id == newDevice.id }

                        val newList = withoutOldDeviceId + newDevice
                        newList
                    }
                }
            }
        } else {
            return object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.d("AndroidNetworkServices", "onResolveFailed: $serviceInfo, $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    Log.d("AndroidNetworkServices", "onServiceResolved: $serviceInfo")
                }
            }
        }
    }

    fun NsdServiceInfo.toNearbyDevice(): NearbyDevice? {
        val deviceUuid =
            serviceInfo.attributes["deviceUuid"]?.toString(Charsets.UTF_8) ?: return null
        val deviceName = serviceInfo.attributes["deviceName"]?.toString(Charsets.UTF_8)
        val deviceType = serviceInfo.attributes["deviceType"]?.toString(Charsets.UTF_8)
        val protocolVersion = serviceInfo.attributes["protocolVersion"]?.toString(Charsets.UTF_8)
        return NearbyDevice(
            id = deviceUuid,
            deviceName = deviceName!!,
            deviceType = deviceType!!,
            protocolVersion = protocolVersion!!,
            port = port,
            serviceName = serviceName,
            serviceType = serviceType,
        )
    }
}