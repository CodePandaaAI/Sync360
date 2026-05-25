package com.liftley.sync360.features.sync.domain.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class AndroidDiscoveryService(private val context: Context) : NetworkDiscoveryService {
    private val _discoveredDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val discoveredDevices: StateFlow<List<DeviceProfile>> = _discoveredDevices.asStateFlow()

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_sync360._tcp"
    private val devicesMap = mutableMapOf<String, DeviceProfile>()
    private val serviceNameToIdMap = mutableMapOf<String, String>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val resolveMutex = Mutex()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    override fun startDiscovery() {
        if (discoveryListener != null) return
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
                discoveryListener = null
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains(serviceType)) {
                    scope.launch {
                        resolveMutex.withLock {
                            val resolved = suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
                                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                        continuation.resume(null)
                                    }

                                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                        continuation.resume(resolvedInfo)
                                    }
                                })
                            }
                            if (resolved != null) {
                                val ip = resolved.host?.hostAddress ?: return@withLock
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
                                    isOnline = true
                                )
                                devicesMap[resolvedId] = device
                                serviceNameToIdMap[resolved.serviceName] = resolvedId
                                _discoveredDevices.value = devicesMap.values.toList()
                            }
                        }
                    }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val resolvedId = serviceNameToIdMap.remove(serviceInfo.serviceName)
                if (resolvedId != null) {
                    devicesMap.remove(resolvedId)
                }
                
                // Fallback cleanup using name comparison
                val lostName = serviceInfo.serviceName.replace('-', ' ')
                val toRemove = devicesMap.filter { it.value.name == lostName }.keys
                toRemove.forEach { devicesMap.remove(it) }
                
                _discoveredDevices.value = devicesMap.values.toList()
            }
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
            discoveryListener = null
        }
    }

    override fun registerHost(port: Int, deviceId: String, deviceName: String, deviceType: String) {
        val safeName = sanitizeServiceName(deviceName, deviceId)
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = safeName
            this.serviceType = this@AndroidDiscoveryService.serviceType
            this.port = port
            setAttribute("type", deviceType)
            setAttribute("deviceId", deviceId)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
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

actual fun createNetworkDiscoveryService(context: Any?): NetworkDiscoveryService {
    if (context !is Context) {
        throw IllegalArgumentException("Context is required for Android NetworkDiscoveryService")
    }
    return AndroidDiscoveryService(context)
}
