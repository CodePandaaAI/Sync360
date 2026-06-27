package com.liftley.sync360.data.remote

import com.liftley.sync360.domain.model.ServerState
import com.liftley.sync360.domain.model.UserDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class IncomingServerRequestsController {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Idle)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    var userDecision: CompletableDeferred<UserDecision>? = null

    fun changeServerState(state: ServerState) {
        _serverState.update { state }
    }

    suspend fun waitForUserDecision(): UserDecision {
        val deferred = CompletableDeferred<UserDecision>()
        userDecision = deferred

        val finalDecision = deferred.await()

        _serverState.update { ServerState.Idle }

        return finalDecision
    }

    fun makeDecision(decision: UserDecision) {
        userDecision?.complete(decision)

        userDecision = null
    }
}