package com.liftley.sync360.features.sync.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.network.*
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AndroidDiscoveryService(private val context: Context) : NetworkDiscoveryService {
    private val _discoveredDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceProfile>> = _discoveredDevices.asStateFlow()
    private val _state = MutableStateFlow(DiscoveryState())
    override val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_sync360._tcp"
    private val devicesMap = mutableMapOf<String, DeviceProfile>()
    private val serviceNameToIdMap = mutableMapOf<String, String>()
    private val devicesLock = Any()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val resolveMutex = Mutex()

    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    @Volatile
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile
    private var scanGeneration = 0L
    @Volatile
    private var registrationGeneration = 0L

    override fun startDiscovery(): DiscoveryCommandResult {
        if (_state.value.scan == DiscoveryScanState.SHUTDOWN) return DiscoveryCommandResult.SHUTDOWN
        if (discoveryListener != null) return DiscoveryCommandResult.ALREADY_ACTIVE
        _state.value = _state.value.copy(
            scan = DiscoveryScanState.STARTING,
            failure = null
        )

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("Sync360:DiscoveryMulticastLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            println("AndroidDiscoveryService: Failed to acquire MulticastLock - ${e.message}")
        }

        val generation = ++scanGeneration
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                if (!isCurrentScan(generation, this)) return
                _state.value = _state.value.copy(scan = DiscoveryScanState.ACTIVE, failure = null)
            }
            override fun onDiscoveryStopped(serviceType: String) {
                if (!isCurrentScan(generation, this)) return
                discoveryListener = null
                releaseMulticastLock()
                _state.value = _state.value.copy(scan = DiscoveryScanState.IDLE)
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!isCurrentScan(generation, this)) return
                runCatching { nsdManager.stopServiceDiscovery(this) }
                discoveryListener = null
                releaseMulticastLock()
                _state.value = _state.value.copy(
                    scan = DiscoveryScanState.FAILED,
                    failure = DiscoveryFailure.SCAN_START_FAILED
                )
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!isCurrentScan(generation, this)) return
                runCatching { nsdManager.stopServiceDiscovery(this) }
                discoveryListener = null
                releaseMulticastLock()
                _state.value = _state.value.copy(
                    scan = DiscoveryScanState.FAILED,
                    failure = DiscoveryFailure.SCAN_STOP_FAILED
                )
            }
            @Suppress("DEPRECATION")
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (
                    isCurrentScan(generation, this) &&
                    serviceInfo.serviceType.contains(serviceType)
                ) {
                    scope.launch {
                        resolveMutex.withLock {
                            if (!isCurrentScanGeneration(generation)) return@withLock
                            val resolved = suspendCancellableCoroutine { continuation ->
                                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                        continuation.resume(null)
                                    }

                                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                        continuation.resume(resolvedInfo)
                                    }
                                })
                            }
                            if (!isCurrentScanGeneration(generation)) return@withLock
                            if (resolved != null) {
                                val ip = resolved.host?.hostAddress
                                ?.takeIf { '.' in it && ':' !in it }
                                ?: return@withLock
                                val typeAttr = resolved.attributes["type"]?.let { String(it) }

                                val resolvedType = when (typeAttr) {
                                    "DESKTOP" -> DeviceType.DESKTOP
                                    "PHONE" -> DeviceType.PHONE
                                    "TABLET" -> DeviceType.TABLET
                                    else -> DeviceType.DESKTOP
                                }

                                val advertisedId = resolved.attributes["deviceId"]?.let { String(it) }
                                val resolvedId = advertisedId?.takeIf { it.isNotBlank() } ?: ip
                                val device = DeviceProfile(
                                    id = resolvedId,
                                    name = resolved.serviceName.replace('-', ' '),
                                    type = resolvedType,
                                    hostAddress = ip,
                                    port = resolved.port,
                                    isOnline = true
                                )
                                publishResolvedDevice(
                                    generation,
                                    resolved.serviceName,
                                    resolvedId,
                                    device
                                )
                            }
                        }
                    }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (!isCurrentScan(generation, this)) return
                scope.launch {
                    resolveMutex.withLock {
                        if (!isCurrentScanGeneration(generation)) return@withLock
                        removeLostDevice(generation, serviceInfo.serviceName)
                    }
                }
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            if (isCurrentScan(generation, listener)) {
                discoveryListener = null
                releaseMulticastLock()
                _state.value = _state.value.copy(
                    scan = DiscoveryScanState.FAILED,
                    failure = DiscoveryFailure.SCAN_START_FAILED
                )
            }
        }.getOrElse {
            return DiscoveryCommandResult.FAILED
        }
        return DiscoveryCommandResult.ACCEPTED
    }

    override fun stopDiscovery(): DiscoveryCommandResult {
        if (_state.value.scan == DiscoveryScanState.SHUTDOWN) return DiscoveryCommandResult.SHUTDOWN
        val listener = discoveryListener ?: return DiscoveryCommandResult.ALREADY_IDLE
        _state.value = _state.value.copy(scan = DiscoveryScanState.STOPPING)
        scanGeneration++
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }

        releaseMulticastLock()

        // Discovery and advertisement have different lifetimes. Keep the host
        // registered so desktops can still find this phone after scan auto-stop.
        _state.value = _state.value.copy(scan = DiscoveryScanState.IDLE)
        return DiscoveryCommandResult.ACCEPTED
    }

    override fun registerHost(
        port: Int,
        deviceId: String,
        deviceName: String,
        deviceType: String
    ): DiscoveryCommandResult {
        if (_state.value.advertisement == DiscoveryAdvertisementState.SHUTDOWN) {
            return DiscoveryCommandResult.SHUTDOWN
        }
        if (registrationListener != null) return DiscoveryCommandResult.ALREADY_ACTIVE
        _state.value = _state.value.copy(
            advertisement = DiscoveryAdvertisementState.REGISTERING,
            failure = null
        )

        val safeName = sanitizeServiceName(deviceName, deviceId)
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = safeName
            this.serviceType = this@AndroidDiscoveryService.serviceType
            this.port = port
            setAttribute("type", deviceType)
            setAttribute("deviceId", deviceId)
        }

        val generation = ++registrationGeneration
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                if (!isCurrentRegistration(generation, this)) return
                _state.value = _state.value.copy(
                    advertisement = DiscoveryAdvertisementState.ACTIVE,
                    failure = null
                )
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (!isCurrentRegistration(generation, this)) return
                registrationListener = null
                _state.value = _state.value.copy(
                    advertisement = DiscoveryAdvertisementState.FAILED,
                    failure = DiscoveryFailure.REGISTRATION_FAILED
                )
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                if (!isCurrentRegistration(generation, this)) return
                registrationListener = null
                _state.value = _state.value.copy(advertisement = DiscoveryAdvertisementState.IDLE)
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                if (!isCurrentRegistration(generation, this)) return
                registrationListener = null
                _state.value = _state.value.copy(
                    advertisement = DiscoveryAdvertisementState.FAILED,
                    failure = DiscoveryFailure.REGISTRATION_FAILED
                )
            }
        }
        registrationListener = listener

        return runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            DiscoveryCommandResult.ACCEPTED
        }.onFailure {
            if (isCurrentRegistration(generation, listener)) {
                registrationListener = null
                _state.value = _state.value.copy(
                    advertisement = DiscoveryAdvertisementState.FAILED,
                    failure = DiscoveryFailure.REGISTRATION_FAILED
                )
            }
        }.getOrDefault(DiscoveryCommandResult.FAILED)
    }

    override fun shutdown() {
        stopDiscovery()
        unregisterHost()
        scanGeneration++
        registrationGeneration++
        scope.cancel()
        clearDiscoveredDevices()
        _state.value = DiscoveryState(
            scan = DiscoveryScanState.SHUTDOWN,
            advertisement = DiscoveryAdvertisementState.SHUTDOWN
        )
    }

    override fun unregisterHost(): DiscoveryCommandResult {
        if (_state.value.advertisement == DiscoveryAdvertisementState.SHUTDOWN) {
            return DiscoveryCommandResult.SHUTDOWN
        }
        val listener = registrationListener ?: return DiscoveryCommandResult.ALREADY_IDLE
        registrationGeneration++
        registrationListener = null
        return runCatching {
            nsdManager.unregisterService(listener)
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.IDLE
            )
            DiscoveryCommandResult.ACCEPTED
        }.onFailure {
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
        }.getOrDefault(DiscoveryCommandResult.FAILED)
    }

    private fun isCurrentScan(
        generation: Long,
        listener: NsdManager.DiscoveryListener
    ): Boolean {
        return generation == scanGeneration &&
            discoveryListener === listener &&
            _state.value.scan != DiscoveryScanState.SHUTDOWN
    }

    private fun isCurrentScanGeneration(generation: Long): Boolean {
        return generation == scanGeneration &&
            discoveryListener != null &&
            _state.value.scan != DiscoveryScanState.SHUTDOWN
    }

    private fun isCurrentRegistration(
        generation: Long,
        listener: NsdManager.RegistrationListener
    ): Boolean {
        return generation == registrationGeneration &&
            registrationListener === listener &&
            _state.value.advertisement != DiscoveryAdvertisementState.SHUTDOWN
    }

    private fun clearDiscoveredDevices() {
        synchronized(devicesLock) {
            devicesMap.clear()
            serviceNameToIdMap.clear()
            _discoveredDevices.value = emptyList()
        }
    }

    private fun publishResolvedDevice(
        generation: Long,
        serviceName: String,
        deviceId: String,
        device: DeviceProfile
    ) {
        synchronized(devicesLock) {
            if (!isCurrentScanGeneration(generation)) return
            devicesMap[deviceId] = device
            serviceNameToIdMap[serviceName] = deviceId
            _discoveredDevices.value = devicesMap.values.toList()
        }
    }

    private fun removeLostDevice(generation: Long, serviceName: String) {
        synchronized(devicesLock) {
            if (!isCurrentScanGeneration(generation)) return
            serviceNameToIdMap.remove(serviceName)?.let(devicesMap::remove)
            val lostName = serviceName.replace('-', ' ')
            devicesMap.entries.removeAll { it.value.name == lostName }
            _discoveredDevices.value = devicesMap.values.toList()
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (e: Exception) {
            println("AndroidDiscoveryService: Failed to release MulticastLock - ${e.message}")
        } finally {
            multicastLock = null
        }
    }

    private fun sanitizeServiceName(name: String, deviceId: String): String {
        val suffix = deviceId.takeLast(8)
        return ("$name-$suffix")
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .trim('-')
            .take(63)
            .ifBlank { "sync360-$suffix" }
    }
}

