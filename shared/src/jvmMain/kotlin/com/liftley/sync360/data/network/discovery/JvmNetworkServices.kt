package com.liftley.sync360.data.network.discovery

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
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

    private val _registrationServiceStatus: MutableStateFlow<RegistrationStatus> =
        MutableStateFlow(RegistrationStatus.Idle)

    override val registrationServiceStatus: StateFlow<RegistrationStatus> =
        _registrationServiceStatus.asStateFlow()

    private val jmDnsByAddress = mutableMapOf<InetAddress, JmDNS>()
    private val listenerByAddress = mutableMapOf<InetAddress, ServiceListener>()
    private val resolvedDevicesByServiceKey = ConcurrentHashMap<String, NearbyDevice>()

    override suspend fun startDiscoveryAndAdvertising(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return

        val registrationIsStarting =
            registrationServiceStatus.value == RegistrationStatus.Idle

        if (
            !registrationIsStarting &&
            registrationServiceStatus.value != RegistrationStatus.Running
        ) {
            return
        }

        _discoveryServiceStatus.value = DiscoveryStatus.Starting
        if (registrationIsStarting) {
            _registrationServiceStatus.value = RegistrationStatus.Starting
        }

        try {
            withContext(Dispatchers.IO) {
                if (registrationIsStarting) {
                    if (jmDnsByAddress.isNotEmpty() && !closeAllInstances()) {
                        error("Could not close the previous JmDNS instances")
                    }
                    startOnLanInterfaces(httpServerPort, fileTransferPort)
                } else {
                    addDiscoveryListeners()
                }
            }

            if (registrationIsStarting) {
                _registrationServiceStatus.value = RegistrationStatus.Running
            }
            _discoveryServiceStatus.value = DiscoveryStatus.Running
        } catch (exception: Exception) {
            val closed = withContext(Dispatchers.IO) { closeAllInstances() }
            _registrationServiceStatus.value = if (closed) RegistrationStatus.Idle else RegistrationStatus.Running
            _discoveryServiceStatus.value = if (closed) DiscoveryStatus.Idle else DiscoveryStatus.CleanupFailed
            exception.printStackTrace()
        }
    }

    override suspend fun stopDiscoveryAndAdvertising() {
        _discoveryServiceStatus.value = DiscoveryStatus.Stopping
        _registrationServiceStatus.value = RegistrationStatus.Stopping
        val closed = withContext(Dispatchers.IO) { closeAllInstances() }
        _discoveryServiceStatus.value = if (closed) DiscoveryStatus.Idle else DiscoveryStatus.CleanupFailed
        _registrationServiceStatus.value = if (closed) RegistrationStatus.Idle else RegistrationStatus.Running
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
                runCatching {
                    jmDns?.close()
                }.onFailure { closeException ->
                    closeException.printStackTrace()
                }
                lastFailure = exception
            }
        }

        synchronized(this) {
            if (jmDnsByAddress.isEmpty()) {
                throw IllegalStateException(
                    "Could not start Sync360 on any active LAN interface",
                    lastFailure
                )
            }
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
            if (
                discoveryServiceStatus.value != DiscoveryStatus.Starting &&
                discoveryServiceStatus.value != DiscoveryStatus.Running
            ) {
                return
            }

            val nearbyDevice = event.info.toNearbyDevice(interfaceAddress) ?: return
            val localDeviceId = localDeviceInfoProvider.getLocalDeviceInfo().deviceId
            if (nearbyDevice.id == localDeviceId) return

            resolvedDevicesByServiceKey[event.serviceKey(interfaceAddress)] = nearbyDevice
            publishMergedDevices()
        }
    }

    @Synchronized
    private fun addDiscoveryListeners() {
        if (listenerByAddress.isEmpty()) {
            error("No registered JmDNS instances are available for discovery")
        }

        val addedListeners = mutableListOf<Pair<JmDNS, ServiceListener>>()

        try {
            listenerByAddress.forEach { (address, listener) ->
                val jmDns = jmDnsByAddress[address] ?: return@forEach
                jmDns.addServiceListener(SERVICE_TYPE, listener)
                addedListeners += jmDns to listener
            }
        } catch (exception: Exception) {
            addedListeners.forEach { (jmDns, listener) ->
                runCatching {
                    jmDns.removeServiceListener(SERVICE_TYPE, listener)
                }
            }
            throw exception
        }
    }

    @Synchronized
    private fun closeAllInstances(): Boolean {
        val closedAddresses = mutableListOf<InetAddress>()

        jmDnsByAddress.forEach { (address, jmDns) ->
            runCatching {
                jmDns.close()
            }.onSuccess {
                closedAddresses += address
            }.onFailure { exception ->
                exception.printStackTrace()
            }
        }

        closedAddresses.forEach { address ->
            jmDnsByAddress.remove(address)
            listenerByAddress.remove(address)
        }
        resolvedDevicesByServiceKey.clear()
        _nearbyDevices.value = emptyList()

        return jmDnsByAddress.isEmpty()
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

    private fun ServiceInfo.toNearbyDevice(interfaceAddress: InetAddress): NearbyDevice? {
        val deviceUuid = getPropertyString("deviceUuid") ?: return null
        val deviceName = getPropertyString("deviceName") ?: return null
        val deviceType = getPropertyString("deviceType") ?: return null
        val protocolVersion = getPropertyString("protocolVersion") ?: return null
        val fileTransferPort = getPropertyString("fileTransferPort")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return null
        val httpPort = port.takeIf { it > 0 } ?: return null
        val networkInterface = runCatching {
            NetworkInterface.getByInetAddress(interfaceAddress)
        }.getOrNull()
        val hostAddresses = buildList {
            addAll(inet4Addresses.map { address -> address.hostAddress })
            addAll(
                inet6Addresses.mapNotNull { address ->
                    address.hostAddressWithScope(networkInterface)
                }
            )
        }
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

    private fun Inet6Address.hostAddressWithScope(
        networkInterface: NetworkInterface?
    ): String? {
        if (!isLinkLocalAddress || scopeId > 0) return hostAddress
        if (networkInterface == null) return null

        return runCatching {
            Inet6Address.getByAddress(null, address, networkInterface).hostAddress
        }.getOrNull()
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
            .filter { address ->
                when (address) {
                    is Inet4Address -> address.isSiteLocalAddress
                    is Inet6Address ->
                        !address.isAnyLocalAddress &&
                            !address.isLoopbackAddress &&
                            !address.isMulticastAddress

                    else -> false
                }
            }
            .distinctBy { address -> address.hostAddress }
            .toList()
            .ifEmpty {
                error("No active multicast-capable LAN interface is available")
            }
    }

    private companion object {
        const val SERVICE_TYPE = "_sync360._tcp.local."
    }
}
