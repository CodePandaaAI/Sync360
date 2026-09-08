package com.liftley.sync360.presentation.receive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.components.IdleReceiveStateUi
import com.liftley.sync360.presentation.receive.components.ReceivedFilesStateUi
import com.liftley.sync360.presentation.receive.components.ReceivedTextStateUi
import com.liftley.sync360.presentation.receive.components.ReceivingFilesStateUi
import com.liftley.sync360.presentation.receive.model.ReceiveState
import org.koin.compose.koinInject

@Composable
fun ReceiveScreen() {
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    Sync360Surface(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        when (val state = receiveScreenState) {
            is ReceiveState.Idle -> {
                IdleReceiveStateUi(
                    fileReceiveCode = state.fileReceiveCode
                )
            }

            is ReceiveState.ReceivedText -> {
                ReceivedTextStateUi(
                    senderDeviceName = state.senderDeviceName,
                    text = state.text,
                    onCopyText = {
                        receiveScreenViewModel.copyReceivedText(state.text)
                        receiveScreenViewModel.clearState()
                    },
                    onClear = receiveScreenViewModel::clearState
                )
            }

            is ReceiveState.ReceivingFiles -> {
                ReceivingFilesStateUi(state)
            }

            is ReceiveState.ReceivedFiles -> {
                ReceivedFilesStateUi(
                    state = state,
                    onOpenDownloads = receiveScreenViewModel::openDownloads,
                    onDone = receiveScreenViewModel::clearState
                )
            }
        }
    }
}
