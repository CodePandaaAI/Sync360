package com.liftley.sync360.data.network.discovery

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import com.liftley.sync360.domain.toNearbyDeviceAndroidImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AndroidNetworkServices(
    context: Context,
    identityStore: LocalDeviceIdentityStore
) : NetworkServices {
    private val nsdManager = requireNotNull(context.getSystemService(NsdManager::class.java))
    private val mainExecutor = context.mainExecutor
    private val deviceUuid = identityStore.getOrCreateDeviceUuid()
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val nearbyDevices = _nearbyDevices.asStateFlow()
    private val _discoveryServiceStatus = MutableStateFlow(DiscoveryStatus.Idle)
    override val discoveryServiceStatus = _discoveryServiceStatus.asStateFlow()
    private val _registrationServiceStatus = MutableStateFlow(RegistrationStatus.Idle)
    override val registrationServiceStatus = _registrationServiceStatus.asStateFlow()
    private var activeDiscoverySession: NearbyDeviceScan? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    // Android 13 supports only one legacy resolution at a time. An old request
    // retains its slot until its callback, but cannot updateResolvedDevice into a new session.
    private val pendingResolutions = ArrayDeque<Pair<NearbyDeviceScan, NsdServiceInfo>>()
    private var isResolvingService = false

    override suspend fun startDiscoveryAndAdvertising(httpServerPort: Int, fileTransferPort: Int) {
        withContext(Dispatchers.Main.immediate) {
            if (discoveryServiceStatus.value == DiscoveryStatus.Idle) startDeviceScan()

            if (registrationServiceStatus.value == RegistrationStatus.Idle) {
                advertiseThisDevice(httpServerPort, fileTransferPort)
            }
        }
    }

    override suspend fun stopDiscoveryAndAdvertising() {
        withContext(Dispatchers.Main.immediate) {

            activeDiscoverySession?.stop()

            val listener = registrationListener
            if (listener != null && registrationServiceStatus.value == RegistrationStatus.Running) {
                _registrationServiceStatus.value = RegistrationStatus.Stopping
                runCatching { nsdManager.unregisterService(listener) }.onFailure {
                    _registrationServiceStatus.value = RegistrationStatus.Running
                    logFailure("Unregister", it)
                }
            }
        }
    }

    private fun startDeviceScan() {
        val session = NearbyDeviceScan()
        activeDiscoverySession = session
        _discoveryServiceStatus.value = DiscoveryStatus.Starting
        runCatching {
            nsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, null as Network?, mainExecutor, session
            )
        }.onFailure {
            activeDiscoverySession = null
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            logFailure("Discover", it)
        }
    }

    private inner class NearbyDeviceScan : NsdManager.DiscoveryListener {
        private var isBrowseStopped = false
        private val discoveredServices = mutableMapOf<String, NsdServiceInfo>()
        private val resolvedDevices = mutableMapOf<String, NearbyDevice>()
        private val serviceInfoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
        private val callbacksBeingRemoved = mutableSetOf<NsdManager.ServiceInfoCallback>()

        private fun isActiveSession() = activeDiscoverySession === this &&
                (discoveryServiceStatus.value == DiscoveryStatus.Starting ||
                        discoveryServiceStatus.value == DiscoveryStatus.Running)

        override fun onDiscoveryStarted(serviceType: String) {
            if (activeDiscoverySession === this) _discoveryServiceStatus.value =
                DiscoveryStatus.Running
        }

        override fun onDiscoveryStopped(serviceType: String) {
            if (activeDiscoverySession !== this) return
            isBrowseStopped = true
            finishSessionCleanup()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            if (activeDiscoverySession !== this) return
            logStatus("Start discovery", errorCode)
            _discoveryServiceStatus.value = DiscoveryStatus.Stopping
            isBrowseStopped = true
            clearNearbyDevices()
            stopTrackingServices()
            finishSessionCleanup()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            if (activeDiscoverySession !== this) return
            logStatus("Stop discovery", errorCode)
            _discoveryServiceStatus.value = DiscoveryStatus.CleanupFailed
        }

        override fun onServiceFound(info: NsdServiceInfo) {
            if (!isActiveSession()) return
            val serviceKey = serviceKey(info)
            if (serviceKey in discoveredServices) return
            discoveredServices[serviceKey] = info
            if (Build.VERSION.SDK_INT >= 34) {
                if (serviceInfoCallbacks.containsKey(serviceKey)) return
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (serviceInfoCallbacks[serviceKey] === this) serviceInfoCallbacks.remove(
                            serviceKey
                        )
                        callbacksBeingRemoved.remove(this)
                        val latestService = discoveredServices.remove(serviceKey)
                        if (isActiveSession() && latestService != null && latestService !== info) {
                            onServiceFound(latestService)
                        }
                        logStatus("Track service", errorCode)
                        finishSessionCleanup()
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        if (serviceInfoCallbacks[serviceKey] === this && this !in callbacksBeingRemoved) {
                            updateResolvedDevice(info, serviceInfo)
                        }
                    }

                    override fun onServiceLost() {
                        if (!isActiveSession() || serviceInfoCallbacks[serviceKey] !== this) return
                        resolvedDevices.remove(serviceKey)
                        publishNearbyDevices()
                    }

                    override fun onServiceInfoCallbackUnregistered() {
                        if (serviceInfoCallbacks[serviceKey] === this) serviceInfoCallbacks.remove(
                            serviceKey
                        )
                        callbacksBeingRemoved.remove(this)
                        finishSessionCleanup()
                        // A service can return before its old callback finishes stopping.
                        if (isActiveSession()) {
                            discoveredServices.remove(serviceKey)
                                ?.let { latestService -> onServiceFound(latestService) }
                        }
                    }
                }
                serviceInfoCallbacks[serviceKey] = callback
                runCatching { nsdManager.registerServiceInfoCallback(info, mainExecutor, callback) }
                    .onFailure {
                        serviceInfoCallbacks.remove(serviceKey)
                        discoveredServices.remove(serviceKey)
                        logFailure("Track service", it)
                    }
            } else {
                pendingResolutions.addLast(this to info)
                resolveNextService()
            }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            if (!isActiveSession()) return
            val serviceKey = serviceKey(info)
            discoveredServices.remove(serviceKey)
            resolvedDevices.remove(serviceKey)
            publishNearbyDevices()
            if (Build.VERSION.SDK_INT >= 34) serviceInfoCallbacks[serviceKey]?.let(::stopTrackingService)
        }

        fun isCurrentService(info: NsdServiceInfo): Boolean {
            return isActiveSession() && discoveredServices[serviceKey(info)] === info
        }

        fun updateResolvedDevice(discoveredService: NsdServiceInfo, info: NsdServiceInfo) {
            if (!isCurrentService(discoveredService)) return
            val serviceKey = serviceKey(discoveredService)
            val device = info.toNearbyDeviceAndroidImpl() ?: return
            if (device.id == deviceUuid) return
            resolvedDevices[serviceKey] = device
            publishNearbyDevices()
        }

        private fun publishNearbyDevices() {
            _nearbyDevices.value = resolvedDevices.values.groupBy { it.id }.values.map { matches ->
                matches.first()
                    .copy(hostAddresses = matches.flatMap { it.hostAddresses }.distinct())
            }
        }

        fun stop() {
            if (discoveryServiceStatus.value != DiscoveryStatus.Running &&
                discoveryServiceStatus.value != DiscoveryStatus.CleanupFailed
            ) return
            _discoveryServiceStatus.value = DiscoveryStatus.Stopping
            clearNearbyDevices()
            stopTrackingServices()
            if (isBrowseStopped) {
                finishSessionCleanup()
            } else {
                runCatching { nsdManager.stopServiceDiscovery(this) }.onFailure {
                    _discoveryServiceStatus.value = DiscoveryStatus.CleanupFailed
                    logFailure("Stop discovery", it)
                }
            }
        }

        private fun clearNearbyDevices() {
            discoveredServices.clear()
            resolvedDevices.clear()
            pendingResolutions.clear()
            _nearbyDevices.value = emptyList()
        }

        private fun stopTrackingServices() {
            if (Build.VERSION.SDK_INT >= 34) serviceInfoCallbacks.values.toList()
                .forEach(::stopTrackingService)
        }

        @RequiresApi(34)
        private fun stopTrackingService(callback: NsdManager.ServiceInfoCallback) {
            if (!callbacksBeingRemoved.add(callback)) return
            runCatching { nsdManager.unregisterServiceInfoCallback(callback) }.onFailure {
                callbacksBeingRemoved.remove(callback)
                // Keep ownership if cleanup failed. Never pretend it was released.
                logFailure("Stop service tracking", it)
            }
        }

        private fun finishSessionCleanup() {
            if (activeDiscoverySession !== this || !isBrowseStopped) return
            if (serviceInfoCallbacks.isEmpty()) {
                activeDiscoverySession = null
                _discoveryServiceStatus.value = DiscoveryStatus.Idle
            } else if (callbacksBeingRemoved.isEmpty()) {
                // No cleanup request is in flight. Allow an explicit retry,
                // but never start another session over callbacks that still belong to this session.
                _discoveryServiceStatus.value = DiscoveryStatus.CleanupFailed
            }
        }
    }

    // Android 13 has no ServiceInfoCallback API; keep the legacy fallback here.
    @Suppress("DEPRECATION")
    private fun resolveNextService() {
        if (isResolvingService) return
        var request = pendingResolutions.removeFirstOrNull()
        while (request != null && !request.first.isCurrentService(request.second)) {
            request = pendingResolutions.removeFirstOrNull()
        }
        val (session, info) = request ?: return
        isResolvingService = true
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                logStatus("Resolve", errorCode)
                isResolvingService = false
                resolveNextService()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                session.updateResolvedDevice(info, serviceInfo)
                isResolvingService = false
                resolveNextService()
            }
        }
        runCatching { nsdManager.resolveService(info, mainExecutor, listener) }.onFailure {
            isResolvingService = false
            logFailure("Resolve", it)
            resolveNextService()
        }
    }

    private fun advertiseThisDevice(httpServerPort: Int, fileTransferPort: Int) {
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                if (registrationListener === this) _registrationServiceStatus.value =
                    RegistrationStatus.Running
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                if (registrationListener !== this) return
                registrationListener = null
                _registrationServiceStatus.value = RegistrationStatus.Idle
                logStatus("Register", errorCode)
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                if (registrationListener !== this) return
                registrationListener = null
                _registrationServiceStatus.value = RegistrationStatus.Idle
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                if (registrationListener !== this) return
                _registrationServiceStatus.value = RegistrationStatus.Running
                logStatus("Unregister", errorCode)
            }
        }
        registrationListener = listener
        _registrationServiceStatus.value = RegistrationStatus.Starting
        runCatching {
            val manufacturer = Build.MANUFACTURER.trim().replaceFirstChar { it.titlecase() }
            val model = Build.MODEL.trim()
            val name = if (model.startsWith(
                    manufacturer,
                    ignoreCase = true
                )
            ) model else "$manufacturer $model"
            val info = NsdServiceInfo().apply {
                serviceType = SERVICE_TYPE
                serviceName = "${Build.MODEL} Sync360"
                port = httpServerPort
                setAttribute("deviceUuid", deviceUuid)
                setAttribute("deviceName", name)
                setAttribute("deviceType", "Android")
                setAttribute("protocolVersion", "1")
                setAttribute("fileTransferPort", fileTransferPort.toString())
            }
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, mainExecutor, listener)
        }.onFailure {
            registrationListener = null
            _registrationServiceStatus.value = RegistrationStatus.Idle
            logFailure("Register", it)
        }
    }

    private fun serviceKey(info: NsdServiceInfo) = "${info.serviceName}|${info.network}"
    private fun logStatus(action: String, status: Int) =
        Log.w("AndroidNetworkServices", "$action failed: $status")

    private fun logFailure(action: String, error: Throwable) =
        Log.w("AndroidNetworkServices", "$action failed", error)

    private companion object {
        const val SERVICE_TYPE = "_sync360._tcp."
    }
}
