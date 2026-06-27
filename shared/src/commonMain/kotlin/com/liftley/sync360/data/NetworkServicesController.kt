package com.liftley.sync360.data

import com.liftley.sync360.data.remote.Sync360HttpServer
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.repository.NetworkServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

class NetworkServicesController(
    private val httpServer: Sync360HttpServer,
    private val networkServices: NetworkServices
) {

    val nearbyDevices = networkServices.nearbyDevices

    val discoveryServiceStatus = networkServices.discoveryServiceStatus

    suspend fun startNetworkServices() {
        httpServer.start()

        networkServices.startNetworkServices()

        delay(15000.milliseconds)

        stopDiscoveryServices()
    }

    suspend fun restartDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {
                networkServices.restartDiscoveryServices()
                delay(15000.milliseconds)

                stopDiscoveryServices()
            }

            DiscoveryStatus.Stopping -> {}
            DiscoveryStatus.Starting -> {}
            DiscoveryStatus.Running -> {
                stopDiscoveryServices()

                discoveryServiceStatus.first {
                    it == DiscoveryStatus.Idle
                }

                networkServices.restartDiscoveryServices()

                delay(15000.milliseconds)

                stopDiscoveryServices()
            }
        }
    }

    fun stopDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {}
            DiscoveryStatus.Stopping -> {}
            DiscoveryStatus.Starting -> {}
            DiscoveryStatus.Running -> {
                networkServices.stopDiscoveryServices()
            }
        }
    }
}