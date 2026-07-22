package com.liftley.sync360.data.network.discovery

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class JvmNetworkServices(
    private val localDeviceInfoProvider: LocalDeviceInfoProvider
) : NetworkServices {
    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    override val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    private val _discoveryServiceStatus = MutableStateFlow(DiscoveryStatus.Idle)
    override val discoveryServiceStatus: StateFlow<DiscoveryStatus> =
        _discoveryServiceStatus.asStateFlow()

    private val jmDnsByAddress = mutableMapOf<InetAddress, JmDNS>()
    private val listenerByAddress = mutableMapOf<InetAddress, ServiceListener>()
    private val resolvedDevicesByServiceKey = ConcurrentHashMap<String, NearbyDevice>()
    private var listenersAreRunning = false

    override suspend fun startNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return

        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        try {
            withContext(Dispatchers.IO) {
                if (jmDnsByAddress.isEmpty()) {
                    startOnLanInterfaces(httpServerPort, fileTransferPort)
                } else {
                    addDiscoveryListeners()
                }
            }
            _discoveryServiceStatus.value = DiscoveryStatus.Running
        } catch (exception: Exception) {
            closeAllInstances()
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            throw exception
        }
    }

    override suspend fun repairNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        _discoveryServiceStatus.value = DiscoveryStatus.Stopping

        withContext(Dispatchers.IO) {
            closeAllInstances()
        }

        _discoveryServiceStatus.value = DiscoveryStatus.Idle

        startNetworkServices(httpServerPort, fileTransferPort)
    }

    override fun restartDiscoveryServices() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return
        if (jmDnsByAddress.isEmpty()) return

        _discoveryServiceStatus.value = DiscoveryStatus.Starting
        _nearbyDevices.value = emptyList()
        resolvedDevicesByServiceKey.clear()
        addDiscoveryListeners()
        _discoveryServiceStatus.value = DiscoveryStatus.Running
    }

    override fun stopDiscoveryServices() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Running) return

        _discoveryServiceStatus.value = DiscoveryStatus.Stopping

        synchronized(this) {
            listenerByAddress.forEach { (address, listener) ->
                jmDnsByAddress[address]?.removeServiceListener(SERVICE_TYPE, listener)
            }
            listenersAreRunning = false
        }

        _discoveryServiceStatus.value = DiscoveryStatus.Idle
    }

    private fun startOnLanInterfaces(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        val addresses = findLanAddresses()
        var lastFailure: Throwable? = null

        addresses.forEach { address ->
            var jmDns: JmDNS? = null
            try {
                val startedJmDns = JmDNS.create(address)
                jmDns = startedJmDns
                val listener = createServiceListener(startedJmDns, address)
                val service = createService(
                    httpServerPort = httpServerPort,
                    fileTransferPort = fileTransferPort
                )

                startedJmDns.registerService(service)
                startedJmDns.addServiceListener(SERVICE_TYPE, listener)

                synchronized(this) {
                    jmDnsByAddress[address] = startedJmDns
                    listenerByAddress[address] = listener
                }
            } catch (exception: Exception) {
                runCatching { jmDns?.close() }
                lastFailure = exception
            }
        }

        synchronized(this) {
            listenersAreRunning = jmDnsByAddress.isNotEmpty()
        }

        if (jmDnsByAddress.isEmpty()) {
            throw IllegalStateException(
                "Could not start nearby-device discovery on any active IPv4 LAN interface",
                lastFailure
            )
        }
    }

    private fun createService(
        httpServerPort: Int,
        fileTransferPort: Int
    ): ServiceInfo {
        val localDevice = localDeviceInfoProvider.getLocalDeviceInfo()

        return ServiceInfo.create(
            SERVICE_TYPE,
            "${localDevice.deviceName} Sync360",
            httpServerPort,
            0,
            0,
            mapOf(
                "deviceUuid" to localDevice.deviceId,
                "deviceName" to localDevice.deviceName,
                "deviceType" to localDevice.deviceType,
                "protocolVersion" to localDevice.protocolVersion,
                "fileTransferPort" to fileTransferPort.toString()
            )
        )
    }

    private fun createServiceListener(
        jmDns: JmDNS,
        interfaceAddress: InetAddress
    ) = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmDns.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            resolvedDevicesByServiceKey.remove(event.serviceKey(interfaceAddress))
            publishMergedDevices()
        }

        override fun serviceResolved(event: ServiceEvent) {
            val nearbyDevice = event.info.toNearbyDevice() ?: return
            val localDeviceId = localDeviceInfoProvider.getLocalDeviceInfo().deviceId
            if (nearbyDevice.id == localDeviceId) return

            resolvedDevicesByServiceKey[event.serviceKey(interfaceAddress)] = nearbyDevice
            publishMergedDevices()
        }
    }

    @Synchronized
    private fun addDiscoveryListeners() {
        if (listenersAreRunning) return

        listenerByAddress.forEach { (address, listener) ->
            jmDnsByAddress[address]?.addServiceListener(SERVICE_TYPE, listener)
        }
        listenersAreRunning = true
    }

    @Synchronized
    private fun closeAllInstances() {
        val instances = jmDnsByAddress.values.toList()

        jmDnsByAddress.clear()
        listenerByAddress.clear()
        resolvedDevicesByServiceKey.clear()
        _nearbyDevices.value = emptyList()
        listenersAreRunning = false

        instances.forEach { jmDns ->
            runCatching { jmDns.close() }
        }
    }

    @Synchronized
    private fun publishMergedDevices() {
        val mergedDevices = resolvedDevicesByServiceKey.values
            .groupBy { device -> device.id }
            .values
            .map { matchingDevices ->
                val firstDevice = matchingDevices.first()
                firstDevice.copy(
                    hostAddresses = matchingDevices
                        .flatMap { device -> device.hostAddresses }
                        .distinct()
                )
            }
            .sortedBy { device -> device.deviceName.lowercase() }

        _nearbyDevices.value = mergedDevices
    }

    private fun ServiceInfo.toNearbyDevice(): NearbyDevice? {
        val deviceUuid = getPropertyString("deviceUuid") ?: return null
        val deviceName = getPropertyString("deviceName") ?: return null
        val deviceType = getPropertyString("deviceType") ?: return null
        val protocolVersion = getPropertyString("protocolVersion") ?: return null
        val fileTransferPort = getPropertyString("fileTransferPort")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val httpPort = port.takeIf { it > 0 } ?: return null
        val hostAddresses = inet4Addresses
            .map { address -> address.hostAddress }
            .distinct()

        if (hostAddresses.isEmpty()) return null

        return NearbyDevice(
            id = deviceUuid,
            deviceName = deviceName,
            deviceType = deviceType,
            protocolVersion = protocolVersion,
            hostAddresses = hostAddresses,
            port = httpPort,
            fileTransferPort = fileTransferPort,
            serviceName = name,
            serviceType = type
        )
    }

    private fun ServiceEvent.serviceKey(interfaceAddress: InetAddress): String {
        return "${interfaceAddress.hostAddress}|$type|$name"
    }

    private fun findLanAddresses(): List<InetAddress> {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

        return interfaces.asSequence()
            .filter { networkInterface ->
                runCatching {
                    networkInterface.isUp &&
                        !networkInterface.isLoopback &&
                        !networkInterface.isVirtual &&
                        networkInterface.supportsMulticast()
                }.getOrDefault(false)
            }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses).asSequence()
            }
            .filterIsInstance<Inet4Address>()
            .filter { address ->
                address.isSiteLocalAddress && !address.isLoopbackAddress
            }
            .distinctBy { address -> address.hostAddress }
            .toList()
            .ifEmpty {
                error("No active multicast-capable IPv4 LAN interface is available")
            }
    }

    private companion object {
        const val SERVICE_TYPE = "_sync360._tcp.local."
    }
}
