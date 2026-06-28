package com.liftley.sync360.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.repository.NetworkServices
import com.liftley.sync360.domain.toNearbyDeviceAndroidImpl
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
        Log.d("Android Network Services", "Created!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    }

    private val _nearbyDevices: MutableStateFlow<List<NearbyDevice>> = MutableStateFlow(emptyList())

    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _discoveryServiceStatus: MutableStateFlow<DiscoveryStatus> =
        MutableStateFlow(DiscoveryStatus.Idle)
    override val discoveryServiceStatus: StateFlow<DiscoveryStatus> =
        _discoveryServiceStatus.asStateFlow()

    val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    val serviceType = "_sync360._tcp."

    val deviceUuid = androidLocalDeviceIdentityStore.getOrCreateDeviceUuid()

    val serviceInfoCallbackMap = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) {
            _discoveryServiceStatus.value = DiscoveryStatus.Running
            Log.d("AndroidNetworkServices", "onDiscoveryStarted: $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            Log.d("AndroidNetworkServices", "onDiscoveryStopped: $serviceType")
        }

        @Suppress("NewApi", "DEPRECATION")
        override fun onServiceFound(foundDiscoveryServiceInfo: NsdServiceInfo?) {
            Log.d("AndroidNetworkServices", "onServiceFound: $foundDiscoveryServiceInfo")
            foundDiscoveryServiceInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

                    val serviceInfoCallbackListener = object : NsdManager.ServiceInfoCallback {
                        var resolvedNearbyDeviceInfo: NearbyDevice? = null

                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                            Log.d(
                                "AndroidNetworkServices",
                                "onServiceInfoCallbackRegistrationFailed: $errorCode"
                            )
                            if (serviceInfoCallbackMap.containsKey(resolvedNearbyDeviceInfo?.id)) {
                                serviceInfoCallbackMap.remove(resolvedNearbyDeviceInfo?.id)
                            }
                        }

                        override fun onServiceInfoCallbackUnregistered() {
                            Log.d("AndroidNetworkServices", "onServiceInfoCallbackUnregistered")
                            resolvedNearbyDeviceInfo = null
                        }

                        override fun onServiceLost() {
                            Log.d("AndroidNetworkServices", "onServiceLost on Resolve")
                            _nearbyDevices.update { currentList ->
                                Log.d(
                                    "AndroidNetworkServices",
                                    "onServiceLost previous list: $currentList"
                                )

                                val listWithoutLostDevice =
                                    currentList.filterNot { it.id == resolvedNearbyDeviceInfo?.id }
                                Log.d(
                                    "AndroidNetworkServices",
                                    "onServiceLost current list: $listWithoutLostDevice"
                                )
                                listWithoutLostDevice
                            }

                            if (serviceInfoCallbackMap.containsKey(resolvedNearbyDeviceInfo?.id)) {
                                val serviceCallbackObject =
                                    serviceInfoCallbackMap.remove(resolvedNearbyDeviceInfo?.id)
                                serviceCallbackObject?.let { listener ->
                                    nsdManager.unregisterServiceInfoCallback(listener)
                                }
                            }
                        }

                        override fun onServiceUpdated(updatedResolvedDeviceInfo: NsdServiceInfo) {
                            Log.d(
                                "AndroidNetworkServices",
                                "onServiceUpdated: $updatedResolvedDeviceInfo"
                            )
                            resolvedNearbyDeviceInfo =
                                updatedResolvedDeviceInfo.toNearbyDeviceAndroidImpl()
                            if (resolvedNearbyDeviceInfo == null) {
                                nsdManager.unregisterServiceInfoCallback(this)
                                return
                            }

                            if (resolvedNearbyDeviceInfo?.id == deviceUuid) return

                            _nearbyDevices.update { currentList ->
                                val newDevice =
                                    resolvedNearbyDeviceInfo ?: return@update currentList
                                val withoutOldDeviceId =
                                    currentList.filterNot { device -> device.id == newDevice.id }

                                val newList = withoutOldDeviceId + newDevice
                                newList
                            }

                            val foundDeviceKey = resolvedNearbyDeviceInfo?.id

                            serviceInfoCallbackMap[foundDeviceKey!!] = this
                        }
                    }

                    nsdManager.registerServiceInfoCallback(
                        foundDiscoveryServiceInfo,
                        executor,
                        serviceInfoCallbackListener
                    )
                } else {
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.d(
                                "AndroidNetworkServices",
                                "onResolveFailed: $serviceInfo, $errorCode"
                            )
                        }

                        override fun onServiceResolved(resolvedDeviceInfo: NsdServiceInfo?) {
                            Log.d(
                                "AndroidNetworkServices",
                                "onServiceResolved: $resolvedDeviceInfo"
                            )
                            _nearbyDevices.update { currentList ->
                                val newDevice = resolvedDeviceInfo?.toNearbyDeviceAndroidImpl()
                                    ?: return@update currentList
                                val withoutOldDeviceId =
                                    currentList.filterNot { device -> device.id == newDevice.id }

                                val newList = withoutOldDeviceId + newDevice
                                newList
                            }
                        }
                    }
                    nsdManager.resolveService(
                        foundDiscoveryServiceInfo,
                        resolveListener
                    )
                }
            }
        }

        override fun onServiceLost(lostServiceInfo: NsdServiceInfo?) {
            Log.d("AndroidNetworkServices", "onServiceLost on Discovery: $lostServiceInfo")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            Log.d("AndroidNetworkServices", "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onStopDiscoveryFailed: $serviceType, $errorCode")
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
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

    override suspend fun startNetworkServices(httpServerPort: Int) {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) {
            Log.d(
                "AndroidNetworkServices",
                "startNetworkServices ignored because status=${discoveryServiceStatus.value}"
            )
            return
        }

        Log.d("AndroidNetworkServices", "startNetworkServices: Starting discovery and registration")
        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = "_sync360._tcp."
            serviceName = "${Build.MODEL} Sync360"
            port = httpServerPort

            setAttribute("deviceUuid", deviceUuid)
            setAttribute("deviceName", Build.MODEL ?: "Android Device")
            setAttribute("deviceType", "Android")
            setAttribute("protocolVersion", "1")
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun stopDiscoveryServices() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Running) {
            Log.d(
                "AndroidNetworkServices",
                "stopDiscoveryServices ignored because status=${discoveryServiceStatus.value}"
            )
            return
        }

        _discoveryServiceStatus.value = DiscoveryStatus.Stopping
        Log.d("AndroidNetworkServices", "stopDiscoveryServices: Stopping Discovery Services")
        nsdManager.stopServiceDiscovery(discoveryListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            clearAndStopResolvingServices()
        }
    }

    override fun restartDiscoveryServices() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) {
            Log.d(
                "AndroidNetworkServices",
                "restartDiscoveryServices ignored because status=${discoveryServiceStatus.value}"
            )
            return
        }
        _discoveryServiceStatus.value = DiscoveryStatus.Starting
        Log.d("AndroidNetworkServices", "restartDiscoveryServices: Restarting discovery")
        _nearbyDevices.update {
            emptyList()
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    @Suppress("NewApi")
    private fun clearAndStopResolvingServices() {
        val callbacks = serviceInfoCallbackMap.values.toList()
        serviceInfoCallbackMap.clear()

        callbacks.forEach { callback ->
            nsdManager.unregisterServiceInfoCallback(callback)
        }
    }
}