package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.server.Sync360HttpServer
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    private var httpServerPort: Int? = null
    private var fileTransferPort: Int? = null
    private val lifecycleMutex = Mutex()
    private val repairRequestMutex = Mutex()

    private var hasStarted = false

    val nearbyDevices = networkServices.nearbyDevices

    val discoveryServiceStatus = networkServices.discoveryServiceStatus

    val registrationServiceStatus = networkServices.registrationServiceStatus

    init {
        controllerScope.launch {
            discoveryServiceStatus.collectLatest { status ->
                if (status == DiscoveryStatus.Running) {
                    delay(DISCOVERY_DURATION_MILLIS.milliseconds)
                    stopDiscoveryServices()
                }
            }
        }
    }

    fun startNetworkServices() {
        controllerScope.launch {
            lifecycleMutex.withLock {
                if (hasStarted) return@withLock

                val startedHttpServerPort = httpServer.start()
                val startedFileTransferPort = fileTransferReceiver.start()

                httpServerPort = startedHttpServerPort
                fileTransferPort = startedFileTransferPort

                networkServices.startNetworkServices(
                    httpServerPort = startedHttpServerPort,
                    fileTransferPort = startedFileTransferPort
                )

                hasStarted = true
            }
        }
    }

    suspend fun restartDiscoveryServices() {
        lifecycleMutex.withLock {
            if (discoveryServiceStatus.value == DiscoveryStatus.Idle) {
                networkServices.restartDiscoveryServices()
            }
        }
    }

    suspend fun repairNetworkServices() {
        if (!repairRequestMutex.tryLock()) return

        try {
            startRepairWhenServicesAreStable()
        } finally {
            repairRequestMutex.unlock()
        }
    }

    private suspend fun startRepairWhenServicesAreStable() {
        val activeHttpServerPort = httpServerPort ?: return
        val activeFileTransferPort = fileTransferPort ?: return

        while (true) {
            val repairStarted = lifecycleMutex.withLock {
                if (servicesAreStable()) {
                    networkServices.repairNetworkServices(
                        httpServerPort = activeHttpServerPort,
                        fileTransferPort = activeFileTransferPort
                    )
                    true
                } else false
            }
            if (repairStarted) return
            delay(500.milliseconds)
        }
    }

    private fun servicesAreStable(): Boolean {
        val discoveryStatus = discoveryServiceStatus.value
        val registrationStatus = registrationServiceStatus.value

        val discoveryIsStable =
            discoveryStatus == DiscoveryStatus.Idle ||
                discoveryStatus == DiscoveryStatus.Running
        val registrationIsStable =
            registrationStatus == RegistrationStatus.Idle ||
                registrationStatus == RegistrationStatus.Running

        return discoveryIsStable && registrationIsStable
    }

    private suspend fun stopDiscoveryServices() {
        lifecycleMutex.withLock {
            if (discoveryServiceStatus.value == DiscoveryStatus.Running) {
                networkServices.stopDiscoveryServices()
            }
        }
    }

    private companion object {
        const val DISCOVERY_DURATION_MILLIS = 60_000L
    }
}
