package com.liftley.sync360.domain.service

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import kotlinx.coroutines.flow.StateFlow

interface NetworkServices {
    val nearbyDevices: StateFlow<List<NearbyDevice>>
    val discoveryServiceStatus: StateFlow<DiscoveryStatus>
    suspend fun startNetworkServices(httpServerPort: Int)

    fun restartDiscoveryServices()

    fun stopDiscoveryServices()
}