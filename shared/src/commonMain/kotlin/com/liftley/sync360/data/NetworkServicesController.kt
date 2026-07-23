package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.server.Sync360HttpServer
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class NetworkServicesController(
    private val httpServer: Sync360HttpServer,
    private val fileTransferReceiver: FileTransferReceiver,
    private val networkServices: NetworkServices,
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var discoveryStopJob: Job? = null
    private var httpServerPort: Int? = null
    private var fileTransferPort: Int? = null
    private val repairMutex = Mutex()

    val nearbyDevices = networkServices.nearbyDevices

    val discoveryServiceStatus = networkServices.discoveryServiceStatus

    suspend fun startNetworkServices() {
        fileTransferReceiver.start()

        val startedHttpServerPort = httpServer.start()
        val startedFileTransferPort = fileTransferReceiver.port

        httpServerPort = startedHttpServerPort
        fileTransferPort = startedFileTransferPort

        networkServices.startNetworkServices(startedHttpServerPort, startedFileTransferPort)
        scheduleDiscoveryStop()
    }

    suspend fun restartDiscoveryServices() {
        when (discoveryServiceStatus.value) {
            DiscoveryStatus.Idle -> {
                networkServices.restartDiscoveryServices()
                scheduleDiscoveryStop()
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

    suspend fun repairNetworkServices() {
        repairMutex.withLock {
            val activeHttpServerPort = httpServerPort ?: return@withLock
            val activeFileTransferPort = fileTransferPort ?: return@withLock

            discoveryStopJob?.cancel()
            networkServices.repairNetworkServices(
                httpServerPort = activeHttpServerPort,
                fileTransferPort = activeFileTransferPort
            )
            scheduleDiscoveryStop()
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

    private fun scheduleDiscoveryStop() {
        discoveryStopJob?.cancel()
        discoveryStopJob = controllerScope.launch {
            delay(DISCOVERY_DURATION_MILLIS.milliseconds)
            stopDiscoveryServices()
        }
    }

    private companion object {
        const val DISCOVERY_DURATION_MILLIS = 60_000L
    }
}
