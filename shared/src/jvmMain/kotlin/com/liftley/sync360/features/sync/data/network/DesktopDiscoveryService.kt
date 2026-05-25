package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DesktopDiscoveryService : NetworkDiscoveryService {
    private val _discoveredDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceProfile>> = _discoveredDevices.asStateFlow()

    private var jmdns: JmDNS? = null
    private val serviceType = "_sync360._tcp.local."
    private val devicesMap = mutableMapOf<String, DeviceProfile>()
    private val serviceNameToIdMap = mutableMapOf<String, String>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mapMutex = Mutex()

    init {
        // Clean shutdown hook to unregister and close sockets when JVM exits or reloads
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                jmdns?.unregisterAllServices()
                jmdns?.close()
            } catch (_: Exception) {}
        })
    }

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns?.requestServiceInfo(event.type, event.name)
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
                        isOnline = true
                    )
                    devicesMap[deviceId] = device
                    serviceNameToIdMap[event.name] = deviceId
                    _discoveredDevices.value = devicesMap.values.toList()
                }
            }
        }
    }

    private fun getActualLocalAddress(): InetAddress {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address
                    }
                }
            }
        } catch (_: Exception) {}
        return try {
            InetAddress.getLocalHost()
        } catch (_: Exception) {
            InetAddress.getLoopbackAddress()
        }
    }

    override fun startDiscovery() {
        try {
            if (jmdns == null) {
                val localAddress = getActualLocalAddress()
                jmdns = JmDNS.create(localAddress)
            }
            // Safely avoid duplicate listener attachments
            try {
                jmdns?.removeServiceListener(serviceType, serviceListener)
            } catch (_: Exception) {}
            jmdns?.addServiceListener(serviceType, serviceListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopDiscovery() {
        try {
            jmdns?.removeServiceListener(serviceType, serviceListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Do NOT close jmdns or set it to null here, so the registered host remains active!
    }

    override fun registerHost(port: Int, deviceId: String, deviceName: String, deviceType: String) {
        try {
            if (jmdns == null) {
                val localAddress = getActualLocalAddress()
                jmdns = JmDNS.create(localAddress)
            }
            // Clear any stale registrations to prevent conflicts on hot-reload/restart
            try {
                jmdns?.unregisterAllServices()
            } catch (_: Exception) {}

            val properties = mapOf("type" to deviceType, "deviceId" to deviceId)
            val serviceInfo = ServiceInfo.create(
                serviceType,
                deviceName,
                port,
                0, 0, // weight, priority
                properties
            )
            jmdns?.registerService(serviceInfo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

actual fun createNetworkDiscoveryService(context: Any?): NetworkDiscoveryService {
    return DesktopDiscoveryService()
}
