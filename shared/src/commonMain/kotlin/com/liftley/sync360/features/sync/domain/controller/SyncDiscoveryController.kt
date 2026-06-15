package com.liftley.sync360.features.sync.domain.controller

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.network.DiscoveryCommandResult
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SyncDiscoveryController(
    private val discoveryService: NetworkDiscoveryService,
    private val localDevice: DeviceProfile,
    private val platformOperations: PlatformOperations,
    private val syncPort: Int = 8080
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _nearbyDevices = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val nearbyDevices: StateFlow<List<DeviceProfile>> = _nearbyDevices.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private var scanJob: Job? = null

    init {
        scope.launch {
            discoveryService.discoveredDevices.collect { discovered ->
                val localAddresses = platformOperations.getNetworkEnvironment().addressSet
                _nearbyDevices.value = discovered.filter { device ->
                    device.id != localDevice.id &&
                        device.hostAddress !in localAddresses &&
                        device.hostAddress != "127.0.0.1" &&
                        device.hostAddress != "localhost"
                }
            }
        }
    }

    fun start(): DiscoveryCommandResult {
        val registration = discoveryService.registerHost(
            port = syncPort,
            deviceId = localDevice.id,
            deviceName = localDevice.name,
            deviceType = localDevice.type.name
        )
        if (
            registration == DiscoveryCommandResult.ACCEPTED ||
            registration == DiscoveryCommandResult.ALREADY_ACTIVE
        ) {
            scan()
        }
        return registration
    }

    fun scan() {
        scanJob?.cancel()
        discoveryService.stopDiscovery()
        val result = discoveryService.startDiscovery()
        if (
            result != DiscoveryCommandResult.ACCEPTED &&
            result != DiscoveryCommandResult.ALREADY_ACTIVE
        ) {
            scanJob = null
            _isScanning.value = false
            return
        }
        _isScanning.value = true
        scanJob = scope.launch {
            delay(SCAN_WINDOW_MILLIS)
            discoveryService.stopDiscovery()
            _isScanning.value = false
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
        discoveryService.stopDiscovery()
        discoveryService.unregisterHost()
        _nearbyDevices.value = emptyList()
    }

    fun shutdown() {
        stop()
        discoveryService.shutdown()
    }

    private companion object {
        const val SCAN_WINDOW_MILLIS = 10_000L
    }
}
