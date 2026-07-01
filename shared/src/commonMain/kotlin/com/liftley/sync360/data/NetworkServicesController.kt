package com.liftley.sync360.data

import com.liftley.sync360.data.remote.server.Sync360HttpServer
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.repository.NetworkServices
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class NetworkServicesController(
    private val httpServer: Sync360HttpServer,
    private val networkServices: NetworkServices
) {

    val nearbyDevices = networkServices.nearbyDevices

    val discoveryServiceStatus = networkServices.discoveryServiceStatus

    suspend fun startNetworkServices() {
        val port = httpServer.start()

        networkServices.startNetworkServices(port)

        delay(30000.milliseconds)

        stopDiscoveryServices()
    }

    suspend fun restartDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {
                networkServices.restartDiscoveryServices()
                delay(30000.milliseconds)

                stopDiscoveryServices()
            }

            DiscoveryStatus.Stopping -> { return }
            DiscoveryStatus.Starting -> { return }
            DiscoveryStatus.Running -> { return }
        }
    }

    fun stopDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> { return }
            DiscoveryStatus.Stopping -> { return }
            DiscoveryStatus.Starting -> { return }
            DiscoveryStatus.Running -> {
                networkServices.stopDiscoveryServices()
            }
        }
    }
}