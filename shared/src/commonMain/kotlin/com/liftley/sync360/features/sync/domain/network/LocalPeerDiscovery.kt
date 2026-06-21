package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import kotlinx.coroutines.flow.StateFlow

interface LocalPeerDiscovery {
    val peers: StateFlow<List<DeviceProfile>>
    val state: StateFlow<LocalPeerDiscoveryState>

    fun advertise(localDevice: DeviceProfile, port: Int): PeerDiscoveryCommandResult
    fun stopAdvertising(): PeerDiscoveryCommandResult
    fun scan(): PeerDiscoveryCommandResult
    fun stopScan(): PeerDiscoveryCommandResult
    fun shutdown()
}

data class LocalPeerDiscoveryState(
    val scan: DiscoveryScanState = DiscoveryScanState.IDLE,
    val advertisement: DiscoveryAdvertisementState = DiscoveryAdvertisementState.IDLE,
    val failure: DiscoveryFailure? = null
)

enum class DiscoveryScanState {
    IDLE,
    STARTING,
    ACTIVE,
    STOPPING,
    FAILED,
    SHUTDOWN
}

enum class DiscoveryAdvertisementState {
    IDLE,
    REGISTERING,
    ACTIVE,
    FAILED,
    SHUTDOWN
}

enum class DiscoveryFailure {
    SCAN_START_FAILED,
    SCAN_STOP_FAILED,
    REGISTRATION_FAILED,
    PLATFORM_UNAVAILABLE
}

enum class PeerDiscoveryCommandResult {
    ACCEPTED,
    ALREADY_ACTIVE,
    ALREADY_IDLE,
    SHUTDOWN,
    FAILED
}

expect fun createLocalPeerDiscovery(context: Any? = null): LocalPeerDiscovery
