package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.liftley.sync360.data.remote.IncomingServerRequestsController
import com.liftley.sync360.domain.model.UserDecision

class ReceiveScreenViewModel(private val incomingServerRequestsController: IncomingServerRequestsController) : ViewModel() {
    val serverState = incomingServerRequestsController.serverState

    fun makeDecision(decision: UserDecision){
        incomingServerRequestsController.makeDecision(decision)
    }
}