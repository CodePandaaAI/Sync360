package com.liftley.sync360.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface NetworkServices {
    val nearbyDevices: StateFlow<List<NearbyDevice>>
    fun startNetworkServices()

    fun restartDiscoveryServices()

    fun stopDiscoveryServices()
}

data class NearbyDevice(
    val id: String,
    val deviceName: String,
    val deviceType: String,
    val protocolVersion: String,
    val port: Int,
    val serviceName: String,
    val serviceType: String
)