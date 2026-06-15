package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.data.network.DesktopDiscoveryService

actual fun createNetworkDiscoveryService(context: Any?): NetworkDiscoveryService {
    return DesktopDiscoveryService()
}
