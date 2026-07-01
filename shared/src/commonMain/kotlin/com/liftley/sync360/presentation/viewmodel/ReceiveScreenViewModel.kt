package com.liftley.sync360.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.liftley.sync360.data.remote.IncomingServerRequestsController
import com.liftley.sync360.domain.model.ClientServerState
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.domain.repository.ClipboardProvider

class ReceiveScreenViewModel(
    private val incomingServerRequestsController: IncomingServerRequestsController,
    private val clipboardProvider: ClipboardProvider
) : ViewModel() {
    val clientServerState = incomingServerRequestsController.clientServerState

    fun makeDecision(decision: UserDecision) {
        incomingServerRequestsController.makeDecision(decision)
    }

    fun copyReceivedText(text: String) {
        clipboardProvider.setLatestClipboardTextAs(text)
    }

    fun clearState() {
        incomingServerRequestsController.clearState()
    }
}