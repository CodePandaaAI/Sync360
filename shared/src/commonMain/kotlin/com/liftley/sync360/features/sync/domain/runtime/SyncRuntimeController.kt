package com.liftley.sync360.features.sync.domain.runtime

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.SyncForegroundServiceMode
import com.liftley.sync360.core.platform.SyncForegroundServiceStatus
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeFailure
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState
import com.liftley.sync360.features.sync.domain.model.SyncSnapshot
import com.liftley.sync360.features.sync.domain.model.SyncStartResult
import com.liftley.sync360.features.sync.domain.model.TransferDirection
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import com.liftley.sync360.features.sync.domain.network.PeerDiscoveryCommandResult
import com.liftley.sync360.features.sync.domain.network.LocalPeerDiscovery
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SyncRuntimeController(
    private val repository: SyncRepository,
    private val platformOperations: PlatformOperations,
    private val peerDiscovery: LocalPeerDiscovery,
    private val localDevice: DeviceProfile,
    private val syncPort: Int = 8080
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleLock = Any()
    private val _state = MutableStateFlow<SyncRuntimeState>(SyncRuntimeState.Stopped)
    private var lastForegroundStatus: SyncForegroundServiceStatus? = null
    private var scanJob: Job? = null
    val state: StateFlow<SyncRuntimeState> = _state.asStateFlow()
    private val incomingDecisionState = combine(
        repository.quickSaveEnabled,
        repository.pendingIncomingOffer
    ) { quickSaveEnabled, pendingIncomingOffer ->
        quickSaveEnabled to pendingIncomingOffer
    }
    val snapshot: StateFlow<SyncSnapshot> = combine(
        state,
        repository.transferSnapshot,
        incomingDecisionState
    ) { runtime, transfer, incomingDecision ->
        SyncSnapshot(
            runtime = runtime,
            transfer = transfer,
            quickSaveEnabled = incomingDecision.first,
            pendingIncomingOffer = incomingDecision.second
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SyncSnapshot(
            transfer = TransferSnapshot()
        )
    )

    init {
        scope.launch {
            snapshot.collect { current ->
                updateForegroundService(current)
            }
        }
    }

    fun start() {
        synchronized(lifecycleLock) {
            if (_state.value !is SyncRuntimeState.Stopped) return
            _state.value = SyncRuntimeState.Starting

            val environment = platformOperations.getNetworkEnvironment()
            _state.value = when (repository.startSync()) {
                SyncStartResult.STARTED,
                SyncStartResult.ALREADY_RUNNING -> {
                    if (environment.isAvailable) {
                        when (startPeerDiscovery()) {
                            PeerDiscoveryCommandResult.ACCEPTED,
                            PeerDiscoveryCommandResult.ALREADY_ACTIVE ->
                                SyncRuntimeState.Ready(environment.addresses.map { it.address })
                            else -> SyncRuntimeState.Degraded(
                                localAddresses = environment.addresses.map { it.address },
                                reason = SyncRuntimeFailure.DISCOVERY_UNAVAILABLE
                            )
                        }
                    } else {
                        SyncRuntimeState.Unavailable(SyncRuntimeFailure.NETWORK_UNAVAILABLE)
                    }
                }
                SyncStartResult.NETWORK_UNAVAILABLE ->
                    SyncRuntimeState.Unavailable(SyncRuntimeFailure.NETWORK_UNAVAILABLE)
                SyncStartResult.SERVER_UNAVAILABLE ->
                    SyncRuntimeState.Unavailable(SyncRuntimeFailure.SERVER_UNAVAILABLE)
                SyncStartResult.RUNTIME_CLOSED ->
                    SyncRuntimeState.Unavailable(SyncRuntimeFailure.RUNTIME_CLOSED)
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            if (_state.value is SyncRuntimeState.Stopped) return
            _state.value = SyncRuntimeState.Stopping
            stopScan()
            repository.stopSync()
            _state.value = SyncRuntimeState.Stopped
            platformOperations.stopService()
            lastForegroundStatus = null
        }
    }

    fun restart() {
        synchronized(lifecycleLock) {
            _state.value = SyncRuntimeState.Stopping
            stopScan()
            repository.stopSync()
            _state.value = SyncRuntimeState.Stopped
            lastForegroundStatus = null
            start()
        }
    }

    fun scan() {
        if (
            _state.value is SyncRuntimeState.Ready ||
            _state.value is SyncRuntimeState.Degraded
        ) {
            scanForDevices()
        }
    }

    fun shutdown() {
        synchronized(lifecycleLock) {
            if (_state.value is SyncRuntimeState.Stopping) return
            _state.value = SyncRuntimeState.Stopping
            shutdownDiscovery()
            repository.shutdownSync()
            _state.value = SyncRuntimeState.Stopped
            platformOperations.stopService()
            lastForegroundStatus = null
        }
    }

    private fun startPeerDiscovery(): PeerDiscoveryCommandResult {
        val registration = peerDiscovery.advertise(localDevice, syncPort)
        if (
            registration == PeerDiscoveryCommandResult.ACCEPTED ||
            registration == PeerDiscoveryCommandResult.ALREADY_ACTIVE
        ) {
            scanForDevices()
        }
        return registration
    }

    private fun scanForDevices() {
        scanJob?.cancel()
        peerDiscovery.stopScan()
        val result = peerDiscovery.scan()
        if (
            result != PeerDiscoveryCommandResult.ACCEPTED &&
            result != PeerDiscoveryCommandResult.ALREADY_ACTIVE
        ) {
            scanJob = null
            return
        }
        scanJob = scope.launch {
            delay(SCAN_WINDOW_MILLIS.milliseconds)
            peerDiscovery.stopScan()
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        peerDiscovery.stopScan()
        peerDiscovery.stopAdvertising()
    }

    private fun shutdownDiscovery() {
        scanJob?.cancel()
        scanJob = null
        peerDiscovery.shutdown()
    }

    private fun updateForegroundService(snapshot: SyncSnapshot) {
        val status = snapshot.toForegroundStatus()
        if (status == null) {
            if (lastForegroundStatus != null) {
                platformOperations.stopService()
                lastForegroundStatus = null
            }
            return
        }
        if (status == lastForegroundStatus) return
        if (lastForegroundStatus == null) {
            platformOperations.startForegroundService(status)
        } else {
            platformOperations.updateForegroundService(status)
        }
        lastForegroundStatus = status
    }

    private fun SyncSnapshot.toForegroundStatus(): SyncForegroundServiceStatus? {
        transfer.progress?.let { progress ->
            val action = if (progress.direction == TransferDirection.RECEIVING) {
                "Receiving"
            } else {
                "Sending"
            }
            return SyncForegroundServiceStatus(
                mode = SyncForegroundServiceMode.TRANSFERRING,
                peerName = progress.peerName,
                detail = "$action ${progress.files.size} file${if (progress.files.size == 1) "" else "s"}",
                progressPercent = progress.percent,
                fileCount = progress.files.size
            )
        }
        transfer.failure?.let { failure ->
            return SyncForegroundServiceStatus(
                mode = SyncForegroundServiceMode.ERROR,
                peerName = failure.peerName,
                detail = failure.message,
                fileCount = 0
            )
        }
        return when (runtime) {
            SyncRuntimeState.Starting -> SyncForegroundServiceStatus(
                mode = SyncForegroundServiceMode.READY,
                detail = "Starting local sharing."
            )
            is SyncRuntimeState.Ready -> SyncForegroundServiceStatus(
                mode = SyncForegroundServiceMode.READY,
                detail = "Ready to receive on your local network."
            )
            is SyncRuntimeState.Degraded -> SyncForegroundServiceStatus(
                mode = SyncForegroundServiceMode.ERROR,
                detail = "Discovery is limited. Manual IP fallback is still available."
            )
            is SyncRuntimeState.Unavailable -> SyncForegroundServiceStatus(
                mode = SyncForegroundServiceMode.ERROR,
                detail = "Local sharing is unavailable."
            )
            SyncRuntimeState.Stopped,
            SyncRuntimeState.Stopping -> null
        }
    }

    private companion object {
        const val SCAN_WINDOW_MILLIS = 10_000L
    }
}
