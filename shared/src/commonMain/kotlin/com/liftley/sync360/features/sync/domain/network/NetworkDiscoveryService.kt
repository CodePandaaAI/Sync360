package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import kotlinx.coroutines.flow.StateFlow

interface NetworkDiscoveryService {
    val discoveredDevices: StateFlow<List<DeviceProfile>>
    val state: StateFlow<DiscoveryState>
    fun startDiscovery(): DiscoveryCommandResult
    fun stopDiscovery(): DiscoveryCommandResult
    fun registerHost(
        port: Int,
        deviceId: String,
        deviceName: String,
        deviceType: String
    ): DiscoveryCommandResult
    fun unregisterHost(): DiscoveryCommandResult
    fun shutdown()
}

data class DiscoveryState(
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

enum class DiscoveryCommandResult {
    ACCEPTED,
    ALREADY_ACTIVE,
    ALREADY_IDLE,
    SHUTDOWN,
    FAILED
}

expect fun createNetworkDiscoveryService(context: Any? = null): NetworkDiscoveryService
