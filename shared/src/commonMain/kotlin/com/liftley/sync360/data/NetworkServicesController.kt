package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.server.Sync360HttpServer
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NetworkServicesController(
    private val httpServer: Sync360HttpServer,
    private val fileTransferReceiver: FileTransferReceiver,
    private val networkServices: NetworkServices,
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var httpServerPort: Int? = null
    private var fileTransferPort: Int? = null

    val nearbyDevices = networkServices.nearbyDevices
    val discoveryServiceStatus = networkServices.discoveryServiceStatus
    val registrationServiceStatus = networkServices.registrationServiceStatus

    fun startNetworkServices() {
        controllerScope.launch {
            startServers()
            startNearbySharing()
        }
    }

    fun stopNetworkServices() {
        controllerScope.launch {
            stopNearbySharing()
        }
    }

    fun retryNearbySharing() {
        controllerScope.launch {
            if (hasCleanupFailure()) {
                stopNearbySharing()
            } else {
                startServers()
                startNearbySharing()
            }
        }
    }

    private suspend fun startServers() {
        if (httpServerPort == null) httpServerPort = httpServer.start()
        if (fileTransferPort == null) fileTransferPort = fileTransferReceiver.start()
    }

    private suspend fun startNearbySharing() {
        networkServices.startDiscovery()
        networkServices.startAdvertising(
            httpServerPort = checkNotNull(httpServerPort),
            fileTransferPort = checkNotNull(fileTransferPort)
        )
    }

    private suspend fun stopNearbySharing() {
        networkServices.stopDiscovery()
        networkServices.stopAdvertising()
    }

    private fun hasCleanupFailure(): Boolean {
        return discoveryServiceStatus.value == DiscoveryStatus.CleanupFailed ||
                registrationServiceStatus.value == RegistrationStatus.CleanupFailed
    }
}
