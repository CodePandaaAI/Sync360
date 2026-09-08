package com.liftley.sync360.domain.service

import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.NearbyDevice
import com.liftley.sync360.domain.model.RegistrationStatus
import kotlinx.coroutines.flow.StateFlow

interface NetworkServices {
    val nearbyDevices: StateFlow<List<NearbyDevice>>
    val discoveryServiceStatus: StateFlow<DiscoveryStatus>
    val registrationServiceStatus: StateFlow<RegistrationStatus>

    suspend fun startDiscoveryAndAdvertising(httpServerPort: Int, fileTransferPort: Int)

    /** Stops discovery and advertising, not transfer listeners. */
    suspend fun stopDiscoveryAndAdvertising()
}
