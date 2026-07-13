package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.server.Sync360HttpServer
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class NetworkServicesController(
    private val httpServer: Sync360HttpServer,
    private val fileTransferReceiver: FileTransferReceiver,
    private val networkServices: NetworkServices,
) {

    val nearbyDevices = networkServices.nearbyDevices

    val discoveryServiceStatus = networkServices.discoveryServiceStatus

    suspend fun startNetworkServices() {
        fileTransferReceiver.start()

        val port = httpServer.start()
        val fileTransferPort = fileTransferReceiver.port

        networkServices.startNetworkServices(port, fileTransferPort)

        delay(60000.milliseconds)

        stopDiscoveryServices()
    }

    suspend fun restartDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {
                networkServices.restartDiscoveryServices()
                delay(60000.milliseconds)

                stopDiscoveryServices()
            }

            DiscoveryStatus.Stopping -> {
                return
            }

            DiscoveryStatus.Starting -> {
                return
            }

            DiscoveryStatus.Running -> {
                return
            }
        }
    }

    fun stopDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {
                return
            }

            DiscoveryStatus.Stopping -> {
                return
            }

            DiscoveryStatus.Starting -> {
                return
            }

            DiscoveryStatus.Running -> {
                networkServices.stopDiscoveryServices()
            }
        }
    }
}
