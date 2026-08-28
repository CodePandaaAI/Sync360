package com.liftley.sync360.presentation.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liftley.sync360.data.IncomingServerRequestsController
import com.liftley.sync360.domain.model.ClientServerState
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

    private val _screenState = MutableStateFlow<ReceiveScreenState>(
        ReceiveScreenState.Idle(
            fileReceiveCode = incomingServerRequestsController.fileReceiveCode
        )
    )
    val screenState: StateFlow<ReceiveScreenState> = _screenState.asStateFlow()

    init {
        viewModelScope.launch {
            incomingServerRequestsController.clientServerState.collect { state ->
                _screenState.value = state.toReceiveScreenState(
                    fileReceiveCode = incomingServerRequestsController.fileReceiveCode
                )
            }
        }
    }

    fun copyReceivedText(text: String) {
        clipboardProvider.setLatestClipboardTextAs(text)
    }

    fun clearState() {
        viewModelScope.launch {
            incomingServerRequestsController.clearState()
        }
    }

    fun openDownloads() {
        downloadsFolderOpener.openDownloads()
    }

}

private fun ClientServerState.toReceiveScreenState(
    fileReceiveCode: String
): ReceiveScreenState {
    return when (this) {
        ClientServerState.Idle -> {
            ReceiveScreenState.Idle(fileReceiveCode = fileReceiveCode)
        }

        is ClientServerState.TextReceived -> {
            ReceiveScreenState.ReceivedText(
                senderDeviceName = senderDeviceName,
                text = text
            )
        }

        is ClientServerState.ReceivingFiles -> {
            ReceiveScreenState.ReceivingFiles(
                senderDeviceName = fileOffer.senderDeviceName,
                fileCount = fileOffer.offeredFiles.size,
                completedFileCount = completedFileCount,
                progress = progress
            )
        }

        is ClientServerState.FilesReceived -> {
            ReceiveScreenState.ReceivedFiles(
                senderDeviceName = senderDeviceName,
                fileCount = fileCount
            )
        }
    }
}
