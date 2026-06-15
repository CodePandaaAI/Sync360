package com.liftley.sync360.features.sync.data.network

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopDiscoveryService : NetworkDiscoveryService {
    private val _discoveredDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceProfile>> = _discoveredDevices.asStateFlow()
    private val _state = MutableStateFlow(DiscoveryState())
    override val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val jmdnsInstances = mutableListOf<JmDNS>()
    private val serviceType = "_sync360._tcp.local."
    private val discoveryTypes = listOf("_sync360._tcp.local.", "_sync360._tcp")
    private val devicesMap = mutableMapOf<String, DeviceProfile>()
    private val serviceNameToIdMap = mutableMapOf<String, String>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mapMutex = Mutex()
    private val lifecycleLock = Any()
    private var isDiscovering = false
    private var isShutdown = false

    init {
        System.setProperty("java.net.preferIPv4Stack", "true")
    }

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdnsInstances.forEach { instance ->
                try {
                    instance.requestServiceInfo(event.type, event.name, true)
                } catch (_: Exception) {}
            }
        }

        override fun serviceRemoved(event: ServiceEvent) {
            scope.launch {
                mapMutex.withLock {
                    val id = serviceNameToIdMap.remove(event.name)
                    if (id != null) {
                        devicesMap.remove(id)
                    }
                    val lostName = event.name.replace('-', ' ')
                    val toRemove = devicesMap.filter { it.value.name == lostName }.keys
                    toRemove.forEach { devicesMap.remove(it) }
                    _discoveredDevices.value = devicesMap.values.toList()
                }
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            scope.launch {
                mapMutex.withLock {
                    val ip = event.info.hostAddresses.firstOrNull { it.contains('.') } ?: return@withLock
                    val typeAttr = event.info.getPropertyString("type")
                    val resolvedType = when (typeAttr) {
                        "DESKTOP" -> DeviceType.DESKTOP
                        "PHONE" -> DeviceType.PHONE
                        "TABLET" -> DeviceType.TABLET
                        else -> DeviceType.PHONE
                    }
                    val advertisedId = event.info.getPropertyString("deviceId")
                    val deviceId = advertisedId?.takeIf { it.isNotBlank() } ?: ip
                    val device = DeviceProfile(
                        id = deviceId,
                        name = event.name.replace('-', ' '),
                        type = resolvedType,
                        hostAddress = ip,
                        port = event.info.port,
                        isOnline = true
                    )
                    devicesMap[deviceId] = device
                    serviceNameToIdMap[event.name] = deviceId
                    _discoveredDevices.value = devicesMap.values.toList()
                }
            }
        }
    }

    private fun getActualLocalAddresses(): List<InetAddress> =
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUsableInterface() }
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses.toList()
                        .filter { address -> !address.isLoopbackAddress && address is Inet4Address }
                }
        }.getOrDefault(emptyList()).ifEmpty {
            listOf(
                runCatching { InetAddress.getLocalHost() }
                    .getOrDefault(InetAddress.getLoopbackAddress())
            )
        }

    private fun NetworkInterface.isUsableInterface(): Boolean {
        if (!isUp || isLoopback || isVirtual) return false
        val id = "$name $displayName".lowercase()
        val blocked = listOf(
            "virtual", "hyper-v", "host-only", "wsl", "vmware", "vbox",
            "vpn", "virtualbox", "zerotier", "docker", "vethernet"
        )
        return blocked.none { it in id }
    }

    private fun ensureJmDnsInstances() {
        if (isShutdown) return
        if (jmdnsInstances.isNotEmpty()) return
        getActualLocalAddresses().forEach { address ->
            runCatching {
                jmdnsInstances += JmDNS.create(address)
            }.onFailure { it.printStackTrace() }
        }
    }

    override fun startDiscovery(): DiscoveryCommandResult {
        val shouldStart = synchronized(lifecycleLock) {
            if (isShutdown || isDiscovering) false else {
                isDiscovering = true
                true
            }
        }
        if (!shouldStart) {
            return if (isShutdown) DiscoveryCommandResult.SHUTDOWN
            else DiscoveryCommandResult.ALREADY_ACTIVE
        }
        _state.value = _state.value.copy(scan = DiscoveryScanState.STARTING, failure = null)

        return try {
            ensureJmDnsInstances()
            if (jmdnsInstances.isEmpty()) {
                synchronized(lifecycleLock) { isDiscovering = false }
                _state.value = _state.value.copy(
                    scan = DiscoveryScanState.FAILED,
                    failure = DiscoveryFailure.PLATFORM_UNAVAILABLE
                )
                return DiscoveryCommandResult.FAILED
            }
            // Safely avoid duplicate listener attachments
            jmdnsInstances.forEach { instance ->
                discoveryTypes.forEach { type ->
                    try {
                        instance.removeServiceListener(type, serviceListener)
                    } catch (_: Exception) {}
                    instance.addServiceListener(type, serviceListener)
                }
            }
            _state.value = _state.value.copy(scan = DiscoveryScanState.ACTIVE, failure = null)
            DiscoveryCommandResult.ACCEPTED
        } catch (e: Exception) {
            synchronized(lifecycleLock) { isDiscovering = false }
            _state.value = _state.value.copy(
                scan = DiscoveryScanState.FAILED,
                failure = DiscoveryFailure.SCAN_START_FAILED
            )
            e.printStackTrace()
            DiscoveryCommandResult.FAILED
        }
    }

    override fun stopDiscovery(): DiscoveryCommandResult {
        val shouldStop = synchronized(lifecycleLock) {
            if (!isDiscovering) false else {
                isDiscovering = false
                true
            }
        }
        if (!shouldStop) {
            return if (isShutdown) DiscoveryCommandResult.SHUTDOWN
            else DiscoveryCommandResult.ALREADY_IDLE
        }
        _state.value = _state.value.copy(scan = DiscoveryScanState.STOPPING)

        return try {
            jmdnsInstances.forEach { instance ->
                discoveryTypes.forEach { type ->
                    try {
                        instance.removeServiceListener(type, serviceListener)
                    } catch (_: Exception) {}
                }
            }
            _state.value = _state.value.copy(scan = DiscoveryScanState.IDLE)
            DiscoveryCommandResult.ACCEPTED
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                scan = DiscoveryScanState.FAILED,
                failure = DiscoveryFailure.SCAN_STOP_FAILED
            )
            e.printStackTrace()
            DiscoveryCommandResult.FAILED
        }
        // Do NOT close jmdns or set it to null here, so the registered host remains active!
    }

    override fun registerHost(
        port: Int,
        deviceId: String,
        deviceName: String,
        deviceType: String
    ): DiscoveryCommandResult {
        if (isShutdown) return DiscoveryCommandResult.SHUTDOWN
        _state.value = _state.value.copy(
            advertisement = DiscoveryAdvertisementState.REGISTERING,
            failure = null
        )
        return try {
            ensureJmDnsInstances()
            if (jmdnsInstances.isEmpty()) {
                _state.value = _state.value.copy(
                    advertisement = DiscoveryAdvertisementState.FAILED,
                    failure = DiscoveryFailure.PLATFORM_UNAVAILABLE
                )
                return DiscoveryCommandResult.FAILED
            }
            // Clear any stale registrations to prevent conflicts on hot-reload/restart
            jmdnsInstances.forEach { instance ->
                try {
                    instance.unregisterAllServices()
                } catch (_: Exception) {}
            }

            val properties = mapOf("type" to deviceType, "deviceId" to deviceId)
            jmdnsInstances.forEachIndexed { index, instance ->
                val serviceInfo = ServiceInfo.create(
                    serviceType,
                    if (index == 0) deviceName else "$deviceName-$index",
                    port,
                    0, 0,
                    properties
                )
                instance.registerService(serviceInfo)
            }
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.ACTIVE,
                failure = null
            )
            DiscoveryCommandResult.ACCEPTED
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
            e.printStackTrace()
            DiscoveryCommandResult.FAILED
        }
    }

    override fun shutdown() {
        val shouldShutdown = synchronized(lifecycleLock) {
            if (isShutdown) false else {
                isShutdown = true
                true
            }
        }
        if (!shouldShutdown) return

        stopDiscovery()
        unregisterHost()
        jmdnsInstances.forEach { instance -> runCatching { instance.close() } }
        jmdnsInstances.clear()
        devicesMap.clear()
        serviceNameToIdMap.clear()
        _discoveredDevices.value = emptyList()
        _state.value = DiscoveryState(
            scan = DiscoveryScanState.SHUTDOWN,
            advertisement = DiscoveryAdvertisementState.SHUTDOWN
        )
        scope.cancel()
    }

    override fun unregisterHost(): DiscoveryCommandResult {
        if (isShutdown && jmdnsInstances.isEmpty()) return DiscoveryCommandResult.SHUTDOWN
        if (_state.value.advertisement == DiscoveryAdvertisementState.IDLE) {
            return DiscoveryCommandResult.ALREADY_IDLE
        }
        return try {
            jmdnsInstances.forEach { it.unregisterAllServices() }
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.IDLE,
                failure = null
            )
            DiscoveryCommandResult.ACCEPTED
        } catch (error: Exception) {
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
            DiscoveryCommandResult.FAILED
        }
    }
}
