package com.liftley.sync360.data.network.discovery

import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private var jmDns: JmDNS? = null
    private var registeredService: ServiceInfo? = null
    private var isListenerRegistered = false
    private val serviceIdsByKey = ConcurrentHashMap<String, String>()

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmDns?.requestServiceInfo(event.type, event.name, true)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            val removedDeviceId = serviceIdsByKey.remove(event.serviceKey()) ?: return
            _nearbyDevices.update { devices ->
                devices.filterNot { device -> device.id == removedDeviceId }
            }
        }

        override fun serviceResolved(event: ServiceEvent) {
            val nearbyDevice = event.info.toNearbyDevice() ?: return
            val localDeviceId = localDeviceInfoProvider.getLocalDeviceInfo().deviceId
            if (nearbyDevice.id == localDeviceId) return

            serviceIdsByKey[event.serviceKey()] = nearbyDevice.id
            _nearbyDevices.update { devices ->
                devices.filterNot { device -> device.id == nearbyDevice.id } + nearbyDevice
            }
        }
    }

    override suspend fun startNetworkServices(
        httpServerPort: Int,
        fileTransferPort: Int
    ) {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return

        _discoveryServiceStatus.value = DiscoveryStatus.Starting

        try {
            withContext(Dispatchers.IO) {
                val activeJmDns = jmDns ?: JmDNS.create(selectLanAddress()).also {
                    jmDns = it
                }

                if (registeredService == null) {
                    val localDevice = localDeviceInfoProvider.getLocalDeviceInfo()
                    val service = ServiceInfo.create(
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
                    activeJmDns.registerService(service)
                    registeredService = service
                }

                addDiscoveryListener(activeJmDns)
            }

            _discoveryServiceStatus.value = DiscoveryStatus.Running
        } catch (exception: Exception) {
            _discoveryServiceStatus.value = DiscoveryStatus.Idle
            throw exception
        }
    }

    override fun restartDiscoveryServices() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Idle) return
        val activeJmDns = jmDns ?: return

        _discoveryServiceStatus.value = DiscoveryStatus.Starting
        _nearbyDevices.value = emptyList()
        serviceIdsByKey.clear()
        addDiscoveryListener(activeJmDns)
        _discoveryServiceStatus.value = DiscoveryStatus.Running
    }

    override fun stopDiscoveryServices() {
        if (discoveryServiceStatus.value != DiscoveryStatus.Running) return

        _discoveryServiceStatus.value = DiscoveryStatus.Stopping
        jmDns?.removeServiceListener(SERVICE_TYPE, serviceListener)
        isListenerRegistered = false
        _discoveryServiceStatus.value = DiscoveryStatus.Idle
    }

    @Synchronized
    private fun addDiscoveryListener(activeJmDns: JmDNS) {
        if (isListenerRegistered) return
        activeJmDns.addServiceListener(SERVICE_TYPE, serviceListener)
        isListenerRegistered = true
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

    private fun ServiceEvent.serviceKey(): String = "$type|$name"

    private fun selectLanAddress(): InetAddress {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

        return interfaces.asSequence()
            .filter { networkInterface ->
                runCatching {
                    networkInterface.isUp &&
                        !networkInterface.isLoopback &&
                        !networkInterface.isVirtual
                }.getOrDefault(false)
            }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses).asSequence()
            }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { address ->
                address.isSiteLocalAddress && !address.isLoopbackAddress
            }
            ?: error("No active IPv4 LAN interface is available for nearby-device discovery")
    }

    private companion object {
        const val SERVICE_TYPE = "_sync360._tcp.local."
    }
}
