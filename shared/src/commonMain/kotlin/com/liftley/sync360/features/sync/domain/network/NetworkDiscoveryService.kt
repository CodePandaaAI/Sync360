package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import kotlinx.coroutines.flow.StateFlow

interface NetworkDiscoveryService {
    val discoveredDevices: StateFlow<List<DeviceProfile>>
    fun startDiscovery()
    fun stopDiscovery()
    fun registerHost(port: Int, deviceId: String, deviceName: String, deviceType: String)
}

expect fun createNetworkDiscoveryService(context: Any? = null): NetworkDiscoveryService
