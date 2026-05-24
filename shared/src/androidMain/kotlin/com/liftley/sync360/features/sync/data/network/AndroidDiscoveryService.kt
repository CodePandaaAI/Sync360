package com.liftley.sync360.features.sync.domain.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidDiscoveryService(private val context: Context) : NetworkDiscoveryService {
    private val _discoveredDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceProfile>> = _discoveredDevices.asStateFlow()

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_sync360._tcp"
    private val devicesMap = mutableMapOf<String, DeviceProfile>()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    override fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains(serviceType)) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val ip = serviceInfo.host.hostAddress ?: return
                            val typeAttr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                serviceInfo.attributes["type"]?.let { String(it) }
                            } else null
                            
                            val resolvedType = when (typeAttr) {
                                "DESKTOP" -> DeviceType.DESKTOP
                                "PHONE" -> DeviceType.PHONE
                                "TABLET" -> DeviceType.TABLET
                                else -> DeviceType.DESKTOP
                            }
                            
                            val advertisedId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                serviceInfo.attributes["deviceId"]?.let { String(it) }
                            } else null
                            val device = DeviceProfile(
                                id = advertisedId?.takeIf { it.isNotBlank() } ?: ip,
                                name = serviceInfo.serviceName,
                                type = resolvedType,
                                hostAddress = ip
                            )
                            devicesMap[serviceInfo.serviceName] = device
                            _discoveredDevices.value = devicesMap.values.toList()
                        }
                    })
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                devicesMap.remove(serviceInfo.serviceName)
                _discoveredDevices.value = devicesMap.values.toList()
            }
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                // Ignore if already stopped
            }
            discoveryListener = null
        }
    }

    override fun registerHost(port: Int, deviceId: String, deviceName: String, deviceType: String) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = deviceName
            this.serviceType = this@AndroidDiscoveryService.serviceType
            this.port = port
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                this.setAttribute("type", deviceType)
                this.setAttribute("deviceId", deviceId)
            }
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }
}

actual fun createNetworkDiscoveryService(context: Any?): NetworkDiscoveryService {
    if (context !is Context) {
        throw IllegalArgumentException("Context is required for Android NetworkDiscoveryService")
    }
    return AndroidDiscoveryService(context)
}
