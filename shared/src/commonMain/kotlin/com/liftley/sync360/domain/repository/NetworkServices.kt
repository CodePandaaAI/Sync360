package com.liftley.sync360.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow

interface NetworkServices {
    val nearbyDevices: MutableStateFlow<List<NearbyDevice>>

    fun startNetworkServices()

    fun stopNetworkServices()
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