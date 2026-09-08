package com.liftley.sync360.data

import com.liftley.sync360.data.network.http.server.Sync360HttpServer
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.domain.model.DiscoveryStatus
import com.liftley.sync360.domain.model.RegistrationStatus
import com.liftley.sync360.domain.service.NetworkServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkServicesController(
    private val httpServer: Sync360HttpServer,
    private val fileTransferReceiver: FileTransferReceiver,
    private val networkServices: NetworkServices,
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val discoveryCommands = Channel<DiscoveryCommand>(Channel.UNLIMITED)
    private var hasInitialized = false
    private var httpServerPort: Int? = null
    private var fileTransferPort: Int? = null
    private var isDiscoveryAllowedByLifecycle = false
    private var startRequested = false
    private var stopRequested = false
    private val _isDiscoveryEnabled = MutableStateFlow(true)
    val isDiscoveryEnabled = _isDiscoveryEnabled.asStateFlow()
    private val _discoveryErrorMessage = MutableStateFlow<String?>(null)
    val discoveryErrorMessage = _discoveryErrorMessage.asStateFlow()

    val nearbyDevices = networkServices.nearbyDevices
    val discoveryServiceStatus = networkServices.discoveryServiceStatus
    val registrationServiceStatus = networkServices.registrationServiceStatus

    init {
        controllerScope.launch {
            discoveryServiceStatus.collect { discoveryCommands.send(DiscoveryCommand.StatusChanged) }
        }
        controllerScope.launch {
            registrationServiceStatus.collect { discoveryCommands.send(DiscoveryCommand.StatusChanged) }
        }
        // Only this loop changes lifecycle intent. Callbacks report status;
        // they never decide to start a replacement session.
        controllerScope.launch {
            for (command in discoveryCommands) {
                try {
                    when (command) {
                        is DiscoveryCommand.Initialize -> {
                            if (!hasInitialized) {
                                isDiscoveryAllowedByLifecycle = command.allowed
                                if (httpServerPort == null) httpServerPort = httpServer.start()
                                if (fileTransferPort == null) fileTransferPort = fileTransferReceiver.start()
                                hasInitialized = true
                                allowDiscoveryRetry()
                            }
                        }
                        is DiscoveryCommand.LifecyclePermission -> {
                            if (isDiscoveryAllowedByLifecycle != command.allowed) {
                                isDiscoveryAllowedByLifecycle = command.allowed
                                allowDiscoveryRetry()
                            }
                        }
                        is DiscoveryCommand.Enable -> {
                            _isDiscoveryEnabled.value = command.enabled
                            allowDiscoveryRetry()
                        }
                        DiscoveryCommand.StatusChanged -> Unit
                    }
                    applyDiscoveryIntent()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    _discoveryErrorMessage.value = "Something went wrong with discovery. Try again."
                    exception.printStackTrace()
                }
            }
        }
    }

    fun startNetworkServices(discoveryAllowedAtStartup: Boolean = true) {
        discoveryCommands.trySend(DiscoveryCommand.Initialize(discoveryAllowedAtStartup))
    }

    fun setDiscoveryAllowedByLifecycle(allowed: Boolean) {
        discoveryCommands.trySend(DiscoveryCommand.LifecyclePermission(allowed))
    }

    fun setDiscoveryEnabled(enabled: Boolean) {
        discoveryCommands.trySend(DiscoveryCommand.Enable(enabled))
    }

    private fun allowDiscoveryRetry() {
        startRequested = false
        stopRequested = false
        _discoveryErrorMessage.value = null
    }

    private suspend fun applyDiscoveryIntent() {
        val discovery = discoveryServiceStatus.value
        val registration = registrationServiceStatus.value
        // Wait for real completion even if the user changes their mind.
        if (discovery == DiscoveryStatus.Starting || discovery == DiscoveryStatus.Stopping ||
            registration == RegistrationStatus.Starting || registration == RegistrationStatus.Stopping
        ) return

        val bothServicesStopped = discovery == DiscoveryStatus.Idle && registration == RegistrationStatus.Idle
        val shouldDiscover = isDiscoveryAllowedByLifecycle && isDiscoveryEnabled.value
        if (!shouldDiscover || discovery == DiscoveryStatus.CleanupFailed) {
            if (!bothServicesStopped) {
                if (!stopRequested) {
                    stopRequested = true
                    networkServices.stopDiscoveryAndAdvertising()
                    discoveryCommands.trySend(DiscoveryCommand.StatusChanged)
                } else {
                    _discoveryErrorMessage.value = "Couldn't stop discovery. Try again."
                }
                return
            }
            stopRequested = false
            _discoveryErrorMessage.value = null
            return
        }

        if (discoveryServiceStatus.value == DiscoveryStatus.Running &&
            registrationServiceStatus.value == RegistrationStatus.Running
        ) {
            _discoveryErrorMessage.value = null
            return
        }
        if (startRequested) {
            _discoveryErrorMessage.value = "Couldn't start discovery. Try again."
            return
        }
        startRequested = true
        // A manual retry can also finish startup if one listener failed initially.
        val listeningHttpPort = httpServerPort ?: httpServer.start().also { httpServerPort = it }
        val listeningFilePort = fileTransferPort ?: fileTransferReceiver.start().also { fileTransferPort = it }
        networkServices.startDiscoveryAndAdvertising(listeningHttpPort, listeningFilePort)
        discoveryCommands.trySend(DiscoveryCommand.StatusChanged)
    }

    private sealed interface DiscoveryCommand {
        data class Initialize(val allowed: Boolean) : DiscoveryCommand
        data class LifecyclePermission(val allowed: Boolean) : DiscoveryCommand
        data class Enable(val enabled: Boolean) : DiscoveryCommand
        data object StatusChanged : DiscoveryCommand
    }
}
