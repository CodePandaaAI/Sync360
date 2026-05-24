package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

class DesktopDiscoveryService : NetworkDiscoveryService {
    private val _discoveredDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceProfile>> = _discoveredDevices.asStateFlow()

    private var jmdns: JmDNS? = null
    private val serviceType = "_sync360._tcp.local."
    private val devicesMap = mutableMapOf<String, DeviceProfile>()

    private val serviceListener = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            jmdns?.requestServiceInfo(event.type, event.name)
        }

        override fun serviceRemoved(event: ServiceEvent) {
            devicesMap.remove(event.name)
            _discoveredDevices.value = devicesMap.values.toList()
        }

        override fun serviceResolved(event: ServiceEvent) {
            val ip = event.info.hostAddresses.firstOrNull() ?: return
            val typeAttr = event.info.getPropertyString("type")
            val resolvedType = when (typeAttr) {
                "DESKTOP" -> DeviceType.DESKTOP
                "PHONE" -> DeviceType.PHONE
                "TABLET" -> DeviceType.TABLET
                else -> DeviceType.PHONE
            }
            val device = DeviceProfile(
                id = ip,
                name = event.name,
                type = resolvedType
            )
            devicesMap[event.name] = device
            _discoveredDevices.value = devicesMap.values.toList()
        }
    }

    override fun startDiscovery() {
        try {
            val localAddress = InetAddress.getLocalHost()
            jmdns = JmDNS.create(localAddress)
            jmdns?.addServiceListener(serviceType, serviceListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopDiscovery() {
        jmdns?.removeServiceListener(serviceType, serviceListener)
        jmdns?.close()
        jmdns = null
    }

    override fun registerHost(port: Int, deviceName: String, deviceType: String) {
        try {
            if (jmdns == null) {
                val localAddress = InetAddress.getLocalHost()
                jmdns = JmDNS.create(localAddress)
            }
            val properties = mapOf("type" to deviceType)
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
