package com.liftley.sync360.data

import com.liftley.sync360.domain.model.ClientServerState
import com.liftley.sync360.domain.model.UserDecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class IncomingServerRequestsController {
    private val _clientServerState: MutableStateFlow<ClientServerState> = MutableStateFlow(ClientServerState.Idle)
    val clientServerState: StateFlow<ClientServerState> = _clientServerState.asStateFlow()

    var userDecision: CompletableDeferred<UserDecision>? = null

    fun changeServerState(state: ClientServerState) {
        _clientServerState.value = state
    }

    suspend fun waitForUserDecision(): UserDecision {
        val deferred = CompletableDeferred<UserDecision>()
        userDecision = deferred

        val finalDecision = deferred.await()

        _clientServerState.update { ClientServerState.Idle }

        return finalDecision
    }

    fun makeDecision(decision: UserDecision) {
        userDecision?.complete(decision)

        userDecision = null
    }

    fun clearState() {
        _clientServerState.value = ClientServerState.Idle
    }
}