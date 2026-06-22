package com.liftley.sync360.features.sync.domain.network

import com.liftley.sync360.features.sync.data.network.DesktopLocalPeerDiscovery

actual fun createLocalPeerDiscovery(context: Any?): LocalPeerDiscovery {
    return DesktopLocalPeerDiscovery()
}
