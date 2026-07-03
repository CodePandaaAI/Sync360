package com.liftley.sync360.presentation.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.presentation.receive.components.IdleReceiveStateUi
import com.liftley.sync360.presentation.receive.components.ReceivedTextStateUi
import com.liftley.sync360.presentation.receive.components.TextOfferStateUi
import com.liftley.sync360.presentation.receive.model.ReceiveScreenState
import org.koin.compose.koinInject

@Composable
fun ReceiveScreen() {
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val receiveScreenState by receiveScreenViewModel.screenState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = receiveScreenState) {
                    ReceiveScreenState.Idle -> {
                        IdleReceiveStateUi()
                    }

                    is ReceiveScreenState.IncomingTextOffer -> {
                        TextOfferStateUi(
                            state = state,
                            onAccept = { receiveScreenViewModel.makeDecision(UserDecision.ACCEPTED) },
                            onDecline = { receiveScreenViewModel.makeDecision(UserDecision.DECLINED) }
                        )
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
                }
            }
        }
    }
}