package com.liftley.sync360.presentation.featureReceive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liftley.sync360.core.designsystem.icons.Emoji_Nature
import com.liftley.sync360.domain.model.ServerState
import com.liftley.sync360.domain.model.UserDecision
import com.liftley.sync360.presentation.presentationComponents.Sync360Surface
import com.liftley.sync360.presentation.viewmodel.ReceiveScreenViewModel
import org.koin.compose.koinInject

@Composable
fun ReceiveScreen() {
    val receiveScreenViewModel = koinInject<ReceiveScreenViewModel>()
    val receiveScreenState by receiveScreenViewModel.serverState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        Sync360Surface {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = receiveScreenState) {
                    is ServerState.Idle -> {
                        Icon(imageVector = Emoji_Nature, null, modifier = Modifier.size(48.dp))
                        Text(
                            "Nothing To Receive Right Now!",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    is ServerState.Busy -> {
                        Text(
                            "Request For ${state.requestType}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = { receiveScreenViewModel.makeDecision(UserDecision.ACCEPTED) }) {
                                Text("Accept")
                            }

                            Button(onClick = { receiveScreenViewModel.makeDecision(UserDecision.DECLINED) }) {
                                Text("Decline")
                            }
                        }
                    }
                }
            }
        }
    }
}