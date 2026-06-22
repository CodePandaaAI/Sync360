package com.liftley.sync360.features.sync.domain.network

import android.content.Context
import com.liftley.sync360.features.sync.data.network.AndroidLocalPeerDiscovery

actual fun createLocalPeerDiscovery(context: Any?): LocalPeerDiscovery {
    if (context !is Context) {
        throw IllegalArgumentException("Context is required for Android LocalPeerDiscovery")
    }
    return AndroidLocalPeerDiscovery(context)
}
