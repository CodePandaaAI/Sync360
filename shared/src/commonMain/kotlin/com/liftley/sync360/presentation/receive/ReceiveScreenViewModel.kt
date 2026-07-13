package com.liftley.sync360.presentation.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.IncomingServerRequestsController
import com.liftley.sync360.domain.model.ClientServerState
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.domain.repository.ClipboardProvider
import com.liftley.sync360.domain.repository.DownloadsFolderOpener
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReceiveScreenViewModel(
    private val incomingServerRequestsController: IncomingServerRequestsController,
    private val clipboardProvider: ClipboardProvider,
    private val downloadsFolderOpener: DownloadsFolderOpener
) : ViewModel() {

    private val _screenState = MutableStateFlow<ReceiveScreenState>(ReceiveScreenState.Idle)
    val screenState: StateFlow<ReceiveScreenState> = _screenState.asStateFlow()

    init {
        viewModelScope.launch {
            incomingServerRequestsController.clientServerState.collect { state ->
                _screenState.value = state.toReceiveScreenState()
            }
        }
    }

    fun makeDecision(decision: UserDecision) {
        incomingServerRequestsController.makeDecision(decision)
    }

    fun copyReceivedText(text: String) {
        clipboardProvider.setLatestClipboardTextAs(text)
    }

    fun clearState() {
        incomingServerRequestsController.clearState()
    }

    fun openDownloads() {
        downloadsFolderOpener.openDownloads()
    }
}

private fun ClientServerState.toReceiveScreenState(): ReceiveScreenState {
    return when (this) {
        ClientServerState.Idle -> ReceiveScreenState.Idle

        is ClientServerState.Busy.FileOffer -> {
            ReceiveScreenState.IncomingFileOffer(
                senderDeviceName = fileOffer.senderDeviceName,
                fileCount = fileOffer.files.size,
                totalSizeBytes = fileOffer.totalSizeBytes
            )
        }

        is ClientServerState.Busy.ReceivingFiles -> {
            ReceiveScreenState.ReceivingFiles(
                senderDeviceName = senderDeviceName,
                fileCount = fileCount,
                completedFileCount = completedFileCount
            )
        }

        is ClientServerState.Busy.TextOffer -> {
            ReceiveScreenState.IncomingTextOffer(
                senderDeviceName = senderDeviceName,
                preview = preview,
                characterCount = characterCount
            )
        }

        is ClientServerState.Received -> {
            ReceiveScreenState.ReceivedText(
                text = data
            )
        }

        is ClientServerState.ReceivedFiles -> {
            ReceiveScreenState.ReceivedFiles(
                senderDeviceName = senderDeviceName,
                fileCount = fileCount
            )
        }
    }
}
