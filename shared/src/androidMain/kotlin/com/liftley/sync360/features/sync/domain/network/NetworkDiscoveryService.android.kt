package com.liftley.sync360.features.sync.domain.network

import android.content.Context
import com.liftley.sync360.features.sync.data.network.AndroidDiscoveryService

actual fun createNetworkDiscoveryService(context: Any?): NetworkDiscoveryService {
    if (context !is Context) {
        throw IllegalArgumentException("Context is required for Android NetworkDiscoveryService")
    }
    return AndroidDiscoveryService(context)
}
