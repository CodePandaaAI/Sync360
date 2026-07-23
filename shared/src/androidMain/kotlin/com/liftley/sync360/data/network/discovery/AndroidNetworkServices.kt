package com.liftley.sync360.data.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.service.NetworkServices
import com.liftley.sync360.domain.toNearbyDeviceAndroidImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

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

    val serviceInfoCallbackMap = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()

    private val repairMutex = Mutex()
    @Volatile
    private var isDiscoveryRunning = false
    @Volatile
    private var isRegistrationRequested = false
    private var discoveryStoppedCompletion: CompletableDeferred<Unit>? = null
    private var serviceUnregisteredCompletion: CompletableDeferred<Unit>? = null

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) {
            isDiscoveryRunning = true
            _discoveryServiceStatus.value = DiscoveryStatus.Running
            Log.d("AndroidNetworkServices", "onDiscoveryStarted: $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            isDiscoveryRunning = false
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            discoveryStoppedCompletion?.complete(Unit)
            discoveryStoppedCompletion = null
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
                            resolvedNearbyDeviceInfo?.id?.let { deviceId ->
                                serviceInfoCallbackMap.remove(deviceId)
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

                            resolvedNearbyDeviceInfo?.id?.let { deviceId ->
                                val serviceCallbackObject = serviceInfoCallbackMap.remove(deviceId)
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
                            val newDevice = updatedResolvedDeviceInfo.toNearbyDeviceAndroidImpl()
                            resolvedNearbyDeviceInfo = newDevice
                            if (newDevice == null) {
                                nsdManager.unregisterServiceInfoCallback(this)
                                return
                            }

                            if (newDevice.id == deviceUuid) return

                            _nearbyDevices.update { currentList ->
                                val withoutOldDeviceId =
                                    currentList.filterNot { device -> device.id == newDevice.id }

                                val newList = withoutOldDeviceId + newDevice
                                newList
                            }

                            serviceInfoCallbackMap[newDevice.id] = this
                        }
                    }

                    nsdManager.registerServiceInfoCallback(
                        foundDiscoveryServiceInfo,
                        executor,
                        serviceInfoCallbackListener
                    )
                } else
                {
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.d(
                                "AndroidNetworkServices",
                                "onResolveFailed: $serviceInfo, $errorCode"
                            )
                        }

                        override fun onServiceResolved(resolvedDeviceInfo: NsdServiceInfo?) {
                            Log.d("AndroidNetworkServices", "onServiceResolved: $resolvedDeviceInfo")

                            val newDevice = resolvedDeviceInfo?.toNearbyDeviceAndroidImpl() ?: return

                            if (newDevice.id == deviceUuid) return

                            _nearbyDevices.update { currentList ->
                                currentList.filterNot { device -> device.id == newDevice.id } + newDevice
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
            isDiscoveryRunning = false
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            Log.d("AndroidNetworkServices", "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onStopDiscoveryFailed: $serviceType, $errorCode")
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            isDiscoveryRunning = false
            discoveryStoppedCompletion?.complete(Unit)
            discoveryStoppedCompletion = null
        }
    }

    val registrationListener = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            isRegistrationRequested = false
            Log.d("AndroidNetworkServices", "onRegistrationFailed: $serviceInfo, $errorCode")
        }

        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
            isRegistrationRequested = true
            Log.d("AndroidNetworkServices", "onServiceRegistered: $serviceInfo")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            isRegistrationRequested = false
            serviceUnregisteredCompletion?.complete(Unit)
            serviceUnregisteredCompletion = null
            Log.d("AndroidNetworkServices", "onServiceUnregistered: $serviceInfo")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            isRegistrationRequested = false
            serviceUnregisteredCompletion?.complete(Unit)
            serviceUnregisteredCompletion = null
            Log.d("AndroidNetworkServices", "onUnregistrationFailed: $serviceInfo, $errorCode")
        }
    }

    override suspend fun startNetworkServices(httpServerPort: Int, fileTransferPort: Int) {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) {
            Log.d(
                "AndroidNetworkServices",
                "startNetworkServices ignored because status=${discoveryServiceStatus.value}"
            )
            return
        }

        Log.d("AndroidNetworkServices", "startNetworkServices: Starting discovery and registration")
        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        val rawManufacturer = Build.MANUFACTURER.trim()
        val rawModel = Build.MODEL.trim()

        // Capitalize the first letter of the manufacturer safely
        val manufacturer = rawManufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        // Avoid names like "Google Google Pixel 6" if the model already contains the brand
        val cleanDeviceName = if (rawModel.startsWith(rawManufacturer, ignoreCase = true)) {
            rawModel
        } else {
            "$manufacturer $rawModel"
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = "_sync360._tcp."
            serviceName = "${Build.MODEL} Sync360"
            port = httpServerPort

            setAttribute("deviceUuid", deviceUuid)
            setAttribute("deviceName", cleanDeviceName)
            setAttribute("deviceType", "Android")
            setAttribute("protocolVersion", "1")

            setAttribute(
                "fileTransferPort",
                fileTransferPort.toString()
            )
        }

        isRegistrationRequested = true
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override suspend fun repairNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        repairMutex.withLock {
            _discoveryServiceStatus.value = DiscoveryStatus.Stopping

            if (isDiscoveryRunning) {
                val stopped = CompletableDeferred<Unit>()
                discoveryStoppedCompletion = stopped

                runCatching {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                }.onFailure {
                    stopped.complete(Unit)
                }

                withTimeoutOrNull(REPAIR_STEP_TIMEOUT_MILLIS.milliseconds) {
                    stopped.await()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                clearAndStopResolvingServices()
            }

            if (isRegistrationRequested) {
                val unregistered = CompletableDeferred<Unit>()
                serviceUnregisteredCompletion = unregistered

                runCatching {
                    nsdManager.unregisterService(registrationListener)
                }.onFailure {
                    unregistered.complete(Unit)
                }

                withTimeoutOrNull(REPAIR_STEP_TIMEOUT_MILLIS.milliseconds) {
                    unregistered.await()
                }
            }

            isDiscoveryRunning = false
            isRegistrationRequested = false
            discoveryStoppedCompletion = null
            serviceUnregisteredCompletion = null
            _nearbyDevices.value = emptyList()
            _discoveryServiceStatus.value = DiscoveryStatus.Idle

            startNetworkServices(httpServerPort, fileTransferPort)
        }
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
            runCatching {
                nsdManager.unregisterServiceInfoCallback(callback)
            }
        }
    }

    private companion object {
        const val REPAIR_STEP_TIMEOUT_MILLIS = 3_000L
    }
}
