package com.liftley.sync360.data.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import com.liftley.sync360.domain.toNearbyDeviceAndroidImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
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

    private val _registrationServiceStatus: MutableStateFlow<RegistrationStatus> =
        MutableStateFlow(RegistrationStatus.Idle)

    override val registrationServiceStatus: StateFlow<RegistrationStatus> =
        _registrationServiceStatus.asStateFlow()

    val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    val serviceType = "_sync360._tcp."

    val deviceUuid = androidLocalDeviceIdentityStore.getOrCreateDeviceUuid()

    val serviceInfoCallbacks: MutableSet<NsdManager.ServiceInfoCallback> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var pendingRepair: PendingRepair? = null

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) {
            _discoveryServiceStatus.value = DiscoveryStatus.Running
            Log.d("AndroidNetworkServices", "onDiscoveryStarted: $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            _nearbyDevices.value = emptyList()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                clearAndStopResolvingServices()
            }

            continuePendingRepairIfReady()
            Log.d("AndroidNetworkServices", "onDiscoveryStopped: $serviceType")
        }

        @Suppress("NewApi", "DEPRECATION")
        override fun onServiceFound(foundDiscoveryServiceInfo: NsdServiceInfo?) {
            if (!discoveryIsActive()) return

            Log.d("AndroidNetworkServices", "onServiceFound: $foundDiscoveryServiceInfo")
            foundDiscoveryServiceInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

                    val serviceInfoCallbackListener = object : NsdManager.ServiceInfoCallback {
                        var resolvedNearbyDeviceInfo: NearbyDevice? = null

                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                            serviceInfoCallbacks.remove(this)
                            Log.d(
                                "AndroidNetworkServices",
                                "onServiceInfoCallbackRegistrationFailed: $errorCode"
                            )
                        }

                        override fun onServiceInfoCallbackUnregistered() {
                            serviceInfoCallbacks.remove(this)
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

                            if (serviceInfoCallbacks.remove(this)) {
                                nsdManager.unregisterServiceInfoCallback(this)
                            }
                        }

                        override fun onServiceUpdated(updatedResolvedDeviceInfo: NsdServiceInfo) {
                            if (!discoveryIsActive()) return

                            Log.d(
                                "AndroidNetworkServices",
                                "onServiceUpdated: $updatedResolvedDeviceInfo"
                            )
                            val newDevice = updatedResolvedDeviceInfo.toNearbyDeviceAndroidImpl()
                            if (newDevice == null) {
                                serviceInfoCallbacks.remove(this)
                                nsdManager.unregisterServiceInfoCallback(this)
                                return
                            }

                            resolvedNearbyDeviceInfo = newDevice

                            if (newDevice.id == deviceUuid) return

                            _nearbyDevices.update { currentList ->
                                val withoutOldDeviceId =
                                    currentList.filterNot { device -> device.id == newDevice.id }

                                val newList = withoutOldDeviceId + newDevice
                                newList
                            }
                        }
                    }

                    serviceInfoCallbacks += serviceInfoCallbackListener
                    runCatching {
                        nsdManager.registerServiceInfoCallback(
                            foundDiscoveryServiceInfo,
                            executor,
                            serviceInfoCallbackListener
                        )
                    }.onFailure { exception ->
                        serviceInfoCallbacks.remove(serviceInfoCallbackListener)
                        Log.d(
                            "AndroidNetworkServices",
                            "registerServiceInfoCallback failed",
                            exception
                        )
                    }
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
                            if (!discoveryIsActive()) return

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
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            _nearbyDevices.value = emptyList()
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("AndroidNetworkServices", "onStopDiscoveryFailed: $serviceType, $errorCode")
            _discoveryServiceStatus.value = DiscoveryStatus.Running
            cancelPendingRepair()
        }
    }

    val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
            _registrationServiceStatus.value = RegistrationStatus.Running
            Log.d("AndroidNetworkServices", "onServiceRegistered: $serviceInfo")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            _registrationServiceStatus.value = RegistrationStatus.Idle
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "onRegistrationFailed: $serviceInfo, $errorCode")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            _registrationServiceStatus.value = RegistrationStatus.Idle
            continuePendingRepairIfReady()
            Log.d("AndroidNetworkServices", "onServiceUnregistered: $serviceInfo")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            _registrationServiceStatus.value = RegistrationStatus.Running
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "onUnregistrationFailed: $serviceInfo, $errorCode")
        }
    }

    override suspend fun startNetworkServices(httpServerPort: Int, fileTransferPort: Int) {
        startDiscoveryService()
        startRegistrationService(httpServerPort, fileTransferPort)
    }

    private fun startDiscoveryService() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) {
            Log.d(
                "AndroidNetworkServices",
                "startDiscoveryService ignored because status=${discoveryServiceStatus.value}"
            )
            return
        }

        Log.d("AndroidNetworkServices", "startDiscoveryService: Starting discovery")
        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        runCatching {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        }.onFailure { exception ->
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            _nearbyDevices.value = emptyList()
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "startDiscoveryService failed", exception)
        }
    }

    private fun startRegistrationService(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (registrationServiceStatus.value != RegistrationStatus.Idle) {
            Log.d(
                "AndroidNetworkServices",
                "startRegistrationService ignored because status=${registrationServiceStatus.value}"
            )
            return
        }

        _registrationServiceStatus.value = RegistrationStatus.Starting
        Log.d("AndroidNetworkServices", "startRegistrationService: Starting registration")

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

        val serviceInfo = runCatching {
            NsdServiceInfo().apply {
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
        }.getOrElse { exception ->
            _registrationServiceStatus.value = RegistrationStatus.Idle
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "Could not create registration service", exception)
            return
        }

        runCatching {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )
        }.onFailure { exception ->
            _registrationServiceStatus.value = RegistrationStatus.Idle
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "startRegistrationService failed", exception)
        }
    }

    override suspend fun repairNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        val discoveryIsStable =
            discoveryServiceStatus.value == DiscoveryStatus.Idle ||
                discoveryServiceStatus.value == DiscoveryStatus.Running
        val registrationIsStable =
            registrationServiceStatus.value == RegistrationStatus.Idle ||
                registrationServiceStatus.value == RegistrationStatus.Running

        if (!discoveryIsStable || !registrationIsStable) return

        pendingRepair = PendingRepair(httpServerPort, fileTransferPort)

        if (discoveryServiceStatus.value == DiscoveryStatus.Running) {
            stopDiscoveryServices()
        }
        if (pendingRepair == null) return

        if (registrationServiceStatus.value == RegistrationStatus.Running) {
            stopRegistrationService()
        }
        if (pendingRepair == null) return

        continuePendingRepairIfReady()
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

        runCatching {
            nsdManager.stopServiceDiscovery(discoveryListener)
        }.onFailure { exception ->
            _discoveryServiceStatus.value = DiscoveryStatus.Running
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "stopDiscoveryServices failed", exception)
        }
    }

    private fun stopRegistrationService() {
        if (registrationServiceStatus.value != RegistrationStatus.Running) {
            Log.d(
                "AndroidNetworkServices",
                "stopRegistrationService ignored because status=${registrationServiceStatus.value}"
            )
            return
        }

        _registrationServiceStatus.value = RegistrationStatus.Stopping
        Log.d("AndroidNetworkServices", "stopRegistrationService: Stopping Registration Service")

        runCatching {
            nsdManager.unregisterService(registrationListener)
        }.onFailure { exception ->
            _registrationServiceStatus.value = RegistrationStatus.Running
            cancelPendingRepair()
            Log.d("AndroidNetworkServices", "stopRegistrationService failed", exception)
        }
    }

    override fun restartDiscoveryServices() {
        if (
            discoveryServiceStatus.value != DiscoveryStatus.Idle ||
            registrationServiceStatus.value != RegistrationStatus.Running
        ) {
            Log.d(
                "AndroidNetworkServices",
                "restartDiscoveryServices ignored because discovery=${discoveryServiceStatus.value}, " +
                    "registration=${registrationServiceStatus.value}"
            )
            return
        }

        _nearbyDevices.value = emptyList()
        startDiscoveryService()
    }

    @Synchronized
    private fun continuePendingRepairIfReady() {
        val repair = pendingRepair ?: return
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return
        if (registrationServiceStatus.value != RegistrationStatus.Idle) return

        pendingRepair = null
        _nearbyDevices.value = emptyList()
        startDiscoveryService()
        startRegistrationService(
            httpServerPort = repair.httpServerPort,
            fileTransferPort = repair.fileTransferPort
        )
    }

    private fun cancelPendingRepair() {
        pendingRepair = null
    }

    private fun discoveryIsActive(): Boolean {
        return discoveryServiceStatus.value == DiscoveryStatus.Starting ||
            discoveryServiceStatus.value == DiscoveryStatus.Running
    }

    @Suppress("NewApi")
    private fun clearAndStopResolvingServices() {
        val callbacks = serviceInfoCallbacks.toList()
        serviceInfoCallbacks.clear()

        callbacks.forEach { callback ->
            runCatching {
                nsdManager.unregisterServiceInfoCallback(callback)
            }
        }
    }

    private data class PendingRepair(
        val httpServerPort: Int,
        val fileTransferPort: Int
    )
}
