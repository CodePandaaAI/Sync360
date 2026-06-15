package com.liftley.sync360.features.sync.domain.runtime

import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeFailure
import com.liftley.sync360.features.sync.domain.model.SyncRuntimeState
import com.liftley.sync360.features.sync.domain.model.SyncStartResult
import com.liftley.sync360.features.sync.domain.model.SyncSnapshot
import com.liftley.sync360.features.sync.domain.model.ConnectionSnapshot
import com.liftley.sync360.features.sync.domain.model.SessionSnapshot
import com.liftley.sync360.features.sync.domain.model.TransferSnapshot
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.controller.SyncDiscoveryController
import com.liftley.sync360.features.sync.domain.network.DiscoveryCommandResult
import com.liftley.sync360.features.sync.domain.diagnostics.SyncDiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncRuntimeController(
    private val repository: SyncRepository,
    private val platformOperations: PlatformOperations,
    private val discoveryController: SyncDiscoveryController,
    val diagnosticLog: SyncDiagnosticLog
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleLock = Any()
    private val _state = MutableStateFlow<SyncRuntimeState>(SyncRuntimeState.Stopped)
    val state: StateFlow<SyncRuntimeState> = _state.asStateFlow()
    val snapshot: StateFlow<SyncSnapshot> = combine(
        state,
        repository.connectionSnapshot,
        repository.sessionSnapshot,
        repository.transferSnapshot
    ) { runtime, connection, session, transfer ->
        SyncSnapshot(runtime, connection, session, transfer)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SyncSnapshot(
            connection = ConnectionSnapshot(),
            session = SessionSnapshot.NoSession,
            transfer = TransferSnapshot()
        )
    )

    init {
        scope.launch {
            var previous = snapshot.value
            snapshot.collect { current ->
                if (previous.runtime != current.runtime) {
                    diagnosticLog.record(
                        subsystem = "runtime",
                        stateBefore = previous.runtime.stateCode(),
                        event = "state_transition",
                        stateAfter = current.runtime.stateCode(),
                        outcomeCode = current.runtime.outcomeCode()
                    )
                }
                if (previous.connection.state != current.connection.state) {
                    diagnosticLog.record(
                        subsystem = "connection",
                        stateBefore = previous.connection.state.stateCode(),
                        event = "state_transition",
                        stateAfter = current.connection.state.stateCode(),
                        outcomeCode = current.connection.state.outcomeCode()
                    )
                }
                if (previous.transfer.state != current.transfer.state) {
                    diagnosticLog.record(
                        subsystem = "transfer",
                        stateBefore = previous.transfer.state.stateCode(),
                        event = "state_transition",
                        stateAfter = current.transfer.state.stateCode(),
                        outcomeCode = current.transfer.state.outcomeCode()
                    )
                }
                previous = current
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
                        when (discoveryController.start()) {
                            DiscoveryCommandResult.ACCEPTED,
                            DiscoveryCommandResult.ALREADY_ACTIVE ->
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
            discoveryController.stop()
            repository.stopSync()
            _state.value = SyncRuntimeState.Stopped
        }
    }

    fun scan() {
        if (
            _state.value is SyncRuntimeState.Ready ||
            _state.value is SyncRuntimeState.Degraded
        ) {
            discoveryController.scan()
        }
    }

    fun shutdown() {
        synchronized(lifecycleLock) {
            if (_state.value is SyncRuntimeState.Stopping) return
            _state.value = SyncRuntimeState.Stopping
            discoveryController.shutdown()
            repository.disconnectAll()
            _state.value = SyncRuntimeState.Stopped
        }
    }
}

private fun Any.stateCode(): String = this::class.simpleName.orEmpty()

private fun Any.outcomeCode(): String = when (this) {
    is com.liftley.sync360.features.sync.domain.model.SyncRuntimeState.Degraded -> reason.name
    is com.liftley.sync360.features.sync.domain.model.SyncRuntimeState.Unavailable -> reason.name
    is com.liftley.sync360.features.sync.domain.model.ConnectionState.Failed -> reason.name
    is com.liftley.sync360.features.sync.domain.model.TransferState.Failed -> "FAILED"
    is com.liftley.sync360.features.sync.domain.model.TransferState.Succeeded -> "SUCCEEDED"
    else -> "OK"
}
