package com.liftley.sync360

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liftley.sync360.core.designsystem.AppTheme
import com.liftley.sync360.features.sync.presentation.DesktopDashboard
import com.liftley.sync360.features.sync.presentation.SyncScreen
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import kotlinx.coroutines.flow.Flow

@Composable
fun App(
    isDesktop: Boolean,
    platformContext: Any? = null,
    serverIp: String = "127.0.0.1",
    serverClientCount: Int = 0,
    serverIncomingFlow: Flow<String>? = null,
    onServerBroadcast: ((String) -> Unit)? = null,
    onStartService: ((String) -> Unit)? = null,
    onStopService: (() -> Unit)? = null,
    onShowOverlay: (() -> Unit)? = null,
    onHideOverlay: (() -> Unit)? = null,
    onReadClipboard: (() -> String?)? = null,
    onWriteClipboard: ((String) -> Unit)? = null,
    onOpenFilePicker: ((mimeType: String, onFileSelected: (name: String, content: ByteArray) -> Unit) -> Unit)? = null,
    onSaveFile: ((name: String, content: ByteArray, onResult: (success: Boolean, path: String?) -> Unit) -> Unit)? = null
) {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
            ) {
                // Instantiate the ViewModel using the KMP viewModel() function
                val viewModel = viewModel {
                    val db = com.liftley.sync360.core.database.createDatabase(
                        com.liftley.sync360.core.database.createDatabaseDriverFactory(platformContext)
                    )
                    SyncViewModel(
                        isDesktop = isDesktop,
                        database = db,
                        platformContext = platformContext,
                        initialServerIp = serverIp,
                        initialServerClientCount = serverClientCount,
                        serverIncomingFlow = serverIncomingFlow,
                        onServerBroadcast = onServerBroadcast,
                        onStartService = onStartService,
                        onStopService = onStopService,
                        onShowOverlay = onShowOverlay,
                        onHideOverlay = onHideOverlay,
                        onReadClipboard = onReadClipboard,
                        onWriteClipboard = onWriteClipboard,
                        onOpenFilePicker = onOpenFilePicker,
                        onSaveFile = onSaveFile
                    )
                }


                val uiState by viewModel.uiState.collectAsState()

                if (isDesktop) {
                    DesktopDashboard(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                } else {
                    SyncScreen(
                        uiState = uiState,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
    }
}
