package com.liftley.sync360.presentation.receive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.presentation.app.components.Sync360Surface
import com.liftley.sync360.presentation.receive.components.FileOfferStateUi
import com.liftley.sync360.presentation.receive.components.IdleReceiveStateUi
import com.liftley.sync360.presentation.receive.components.ReceivedFilesStateUi
import com.liftley.sync360.presentation.receive.components.ReceivedTextStateUi
import com.liftley.sync360.presentation.receive.components.ReceivingFilesStateUi
import com.liftley.sync360.presentation.receive.components.TextOfferStateUi
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import org.koin.compose.koinInject

@Composable
fun ReceiveScreen(
    onTroubleshootClick: () -> Unit
) {
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    Sync360Surface(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        when (val state = receiveScreenState) {
            ReceiveScreenState.Idle -> {
                IdleReceiveStateUi(
                    onTroubleshootClick = onTroubleshootClick
                )
            }

            is ReceiveScreenState.IncomingTextOffer -> {
                TextOfferStateUi(
                    state = state,
                    onAccept = { receiveScreenViewModel.makeDecision(UserDecision.ACCEPTED) },
                    onDecline = { receiveScreenViewModel.makeDecision(UserDecision.DECLINED) }
                )
            }

            is ReceiveScreenState.IncomingFileOffer -> {
                FileOfferStateUi(
                    state = state,
                    onAccept = { receiveScreenViewModel.makeDecision(UserDecision.ACCEPTED) },
                    onDecline = { receiveScreenViewModel.makeDecision(UserDecision.DECLINED) }
                )
            }

            is ReceiveScreenState.ReceivingFiles -> {
                ReceivingFilesStateUi(state)
            }

            is ReceiveScreenState.ReceivedText -> {
                ReceivedTextStateUi(
                    text = state.text,
                    onCopyText = {
                        receiveScreenViewModel.copyReceivedText(state.text)
                        receiveScreenViewModel.clearState()
                    },
                    onClear = {
                        receiveScreenViewModel.clearState()
                    }
                )
            }

            is ReceiveScreenState.ReceivedFiles -> {
                ReceivedFilesStateUi(
                    state = state,
                    onOpenDownloads = receiveScreenViewModel::openDownloads,
                    onDone = receiveScreenViewModel::clearState
                )
            }
        }
    }
}
