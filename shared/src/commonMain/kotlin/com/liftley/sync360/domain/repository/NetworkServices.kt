package com.liftley.sync360.domain.repository

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import kotlinx.coroutines.flow.StateFlow

interface NetworkServices {
    val nearbyDevices: StateFlow<List<NearbyDevice>>
    val discoveryServiceStatus: StateFlow<DiscoveryStatus>
    fun startNetworkServices()

    fun restartDiscoveryServices()

    fun stopDiscoveryServices()
}