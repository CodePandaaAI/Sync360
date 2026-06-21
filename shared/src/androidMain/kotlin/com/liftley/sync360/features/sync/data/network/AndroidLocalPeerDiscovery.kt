package com.liftley.sync360.features.sync.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.network.DiscoveryAdvertisementState
import com.liftley.sync360.features.sync.domain.network.PeerDiscoveryCommandResult
import com.liftley.sync360.features.sync.domain.network.DiscoveryFailure
import com.liftley.sync360.features.sync.domain.network.DiscoveryScanState
import com.liftley.sync360.features.sync.domain.network.LocalPeerDiscoveryState
import com.liftley.sync360.features.sync.domain.network.LocalPeerDiscovery
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidLocalPeerDiscovery(context: Context) : LocalPeerDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val serviceType = "_sync360._tcp."

    private val _peers = MutableStateFlow<List<DeviceProfile>>(emptyList())
    override val peers: StateFlow<List<DeviceProfile>> = _peers.asStateFlow()

    private val _state = MutableStateFlow(LocalPeerDiscoveryState())
    override val state: StateFlow<LocalPeerDiscoveryState> = _state.asStateFlow()

    private val lock = Any()
    private val peersById = linkedMapOf<String, DeviceProfile>()
    private val peerIdByServiceName = mutableMapOf<String, String>()

    private var scanListener: NsdManager.DiscoveryListener? = null
    private var advertisementListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var isShutdown = false

    override fun advertise(localDevice: DeviceProfile, port: Int): PeerDiscoveryCommandResult {
        if (isShutdown) return PeerDiscoveryCommandResult.SHUTDOWN
        if (advertisementListener != null) return PeerDiscoveryCommandResult.ALREADY_ACTIVE

        _state.value = _state.value.copy(
            advertisement = DiscoveryAdvertisementState.REGISTERING,
            failure = null
        )

        val listener = registrationListener()
        advertisementListener = listener
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localDevice.advertisedName()
            serviceType = this@AndroidLocalPeerDiscovery.serviceType
            this.port = port
            setAttribute("deviceId", localDevice.id)
            setAttribute("type", localDevice.type.name)
        }

        return runCatching {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            PeerDiscoveryCommandResult.ACCEPTED
        }.onFailure {
            advertisementListener = null
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
        }.getOrDefault(PeerDiscoveryCommandResult.FAILED)
    }

    override fun stopAdvertising(): PeerDiscoveryCommandResult {
        if (isShutdown) return PeerDiscoveryCommandResult.SHUTDOWN
        val listener = advertisementListener ?: return PeerDiscoveryCommandResult.ALREADY_IDLE
        advertisementListener = null
        return runCatching {
            nsdManager.unregisterService(listener)
            _state.value = _state.value.copy(advertisement = DiscoveryAdvertisementState.IDLE)
            PeerDiscoveryCommandResult.ACCEPTED
        }.onFailure {
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
        }.getOrDefault(PeerDiscoveryCommandResult.FAILED)
    }

    override fun scan(): PeerDiscoveryCommandResult {
        if (isShutdown) return PeerDiscoveryCommandResult.SHUTDOWN
        if (scanListener != null) return PeerDiscoveryCommandResult.ALREADY_ACTIVE

        clearPeers()
        acquireMulticastLock()
        _state.value = _state.value.copy(scan = DiscoveryScanState.STARTING, failure = null)

        val listener = discoveryListener()
        scanListener = listener
        return runCatching {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            PeerDiscoveryCommandResult.ACCEPTED
        }.onFailure {
            scanListener = null
            releaseMulticastLock()
            _state.value = _state.value.copy(
                scan = DiscoveryScanState.FAILED,
                failure = DiscoveryFailure.SCAN_START_FAILED
            )
        }.getOrDefault(PeerDiscoveryCommandResult.FAILED)
    }

    override fun stopScan(): PeerDiscoveryCommandResult {
        if (isShutdown) return PeerDiscoveryCommandResult.SHUTDOWN
        val listener = scanListener ?: return PeerDiscoveryCommandResult.ALREADY_IDLE
        scanListener = null
        _state.value = _state.value.copy(scan = DiscoveryScanState.STOPPING)
        return runCatching {
            nsdManager.stopServiceDiscovery(listener)
            releaseMulticastLock()
            _state.value = _state.value.copy(scan = DiscoveryScanState.IDLE)
            PeerDiscoveryCommandResult.ACCEPTED
        }.onFailure {
            releaseMulticastLock()
            _state.value = _state.value.copy(
                scan = DiscoveryScanState.FAILED,
                failure = DiscoveryFailure.SCAN_STOP_FAILED
            )
        }.getOrDefault(PeerDiscoveryCommandResult.FAILED)
    }

    override fun shutdown() {
        if (isShutdown) return
        stopScan()
        stopAdvertising()
        isShutdown = true
        releaseMulticastLock()
        clearPeers()
        scope.cancel()
        _state.value = LocalPeerDiscoveryState(
            scan = DiscoveryScanState.SHUTDOWN,
            advertisement = DiscoveryAdvertisementState.SHUTDOWN
        )
    }

    private fun discoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            _state.value = _state.value.copy(scan = DiscoveryScanState.ACTIVE, failure = null)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            scanListener = null
            releaseMulticastLock()
            _state.value = _state.value.copy(scan = DiscoveryScanState.IDLE)
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType != serviceType) return
            scope.launch { resolve(serviceInfo)?.let(::publishPeer) }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            removePeer(serviceInfo.serviceName)
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            stopFailedDiscovery(this, DiscoveryFailure.SCAN_START_FAILED)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            stopFailedDiscovery(this, DiscoveryFailure.SCAN_STOP_FAILED)
        }
    }

    private fun registrationListener() = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.ACTIVE,
                failure = null
            )
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            advertisementListener = null
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            advertisementListener = null
            _state.value = _state.value.copy(advertisement = DiscoveryAdvertisementState.IDLE)
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            advertisementListener = null
            _state.value = _state.value.copy(
                advertisement = DiscoveryAdvertisementState.FAILED,
                failure = DiscoveryFailure.REGISTRATION_FAILED
            )
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(serviceInfo: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    continuation.resume(null)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    continuation.resume(serviceInfo)
                }
            })
        }

    private fun publishPeer(serviceInfo: NsdServiceInfo) {
        val host = serviceInfo.ipv4Host() ?: return
        val id = serviceInfo.attribute("deviceId")?.takeIf { it.isNotBlank() } ?: host
        val peer = DeviceProfile(
            id = id,
            name = serviceInfo.serviceName.replace('-', ' '),
            type = serviceInfo.attribute("type").toDeviceType(),
            hostAddress = host,
            port = serviceInfo.port,
            isOnline = true
        )

        synchronized(lock) {
            peersById[peer.id] = peer
            peerIdByServiceName[serviceInfo.serviceName] = peer.id
            _peers.value = peersById.values.toList()
        }
    }

    private fun removePeer(serviceName: String) {
        synchronized(lock) {
            peerIdByServiceName.remove(serviceName)?.let(peersById::remove)
            _peers.value = peersById.values.toList()
        }
    }

    private fun clearPeers() {
        synchronized(lock) {
            peersById.clear()
            peerIdByServiceName.clear()
            _peers.value = emptyList()
        }
    }

    private fun stopFailedDiscovery(
        listener: NsdManager.DiscoveryListener,
        failure: DiscoveryFailure
    ) {
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        scanListener = null
        releaseMulticastLock()
        _state.value = _state.value.copy(scan = DiscoveryScanState.FAILED, failure = failure)
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("Sync360:PeerDiscovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    private fun DeviceProfile.advertisedName(): String {
        val suffix = id.takeLast(8)
        return "$name-$suffix"
            .lowercase()
            .replace(Regex("[^a-z0-9-]"), "-")
            .trim('-')
            .take(63)
            .ifBlank { "sync360-$suffix" }
    }

    private fun NsdServiceInfo.attribute(key: String): String? = attributes[key]?.decodeToString()

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.ipv4Host(): String? = host?.hostAddress?.takeIf { it.isIpv4() }

    private fun String?.toDeviceType(): DeviceType = when (this) {
        DeviceType.DESKTOP.name -> DeviceType.DESKTOP
        DeviceType.TABLET.name -> DeviceType.TABLET
        DeviceType.PHONE.name -> DeviceType.PHONE
        else -> DeviceType.PHONE
    }

    private fun String.isIpv4(): Boolean = '.' in this && ':' !in this
}
