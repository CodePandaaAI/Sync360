package com.liftley.sync360.core.di

import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.createIncomingMessageNotifier
import com.liftley.sync360.features.sync.data.network.KtorSyncNetworkService
import com.liftley.sync360.features.sync.data.repository.SyncRepositoryImpl
import com.liftley.sync360.features.sync.domain.model.createLocalDeviceProfile
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import com.liftley.sync360.features.sync.domain.network.createNetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.usecase.*
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class AppContainer(
    context: Any?,
    private val isDesktopPlatform: Boolean
) {
    val platformOperations: PlatformOperations = createPlatformOperations(context)

    private val localDevice = createLocalDeviceProfile(
        context = context,
        isDesktop = isDesktopPlatform,
        desktopAddress = platformOperations.getLocalIpAddress()
    )

    private val networkService: SyncNetworkService = KtorSyncNetworkService()
    private val discoveryService: NetworkDiscoveryService = createNetworkDiscoveryService(context)
    private val incomingNotifier: IncomingMessageNotifier = createIncomingMessageNotifier(context)

    val syncRepository: SyncRepository = SyncRepositoryImpl(
        networkService = networkService,
        discoveryService = discoveryService,
        localDevice = localDevice,
        incomingNotifier = incomingNotifier,
        localLanIp = platformOperations.getLocalIpAddress(),
        platformOperations = platformOperations
    )

    private val startSyncUseCase = StartSyncUseCase(syncRepository)
    private val observePairedDevicesUseCase = ObservePairedDevicesUseCase(syncRepository)
    private val observeNearbyDevicesUseCase = ObserveNearbyDevicesUseCase(syncRepository)
    private val observePendingIncomingConnectUseCase = ObservePendingIncomingConnectUseCase(syncRepository)
    private val observePendingOutgoingConnectUseCase = ObservePendingOutgoingConnectUseCase(syncRepository)
    private val observeConnectionStatusUseCase = ObserveConnectionStatusUseCase(syncRepository)
    private val observeActiveDeviceIdUseCase = ObserveActiveDeviceIdUseCase(syncRepository)
    private val observeConversationMessagesUseCase = ObserveConversationMessagesUseCase(syncRepository)
    private val requestConnectUseCase = RequestConnectUseCase(syncRepository)
    private val confirmOutgoingConnectUseCase = ConfirmOutgoingConnectUseCase(syncRepository)
    private val dismissOutgoingConnectUseCase = DismissOutgoingConnectUseCase(syncRepository)
    private val acceptIncomingConnectUseCase = AcceptIncomingConnectUseCase(syncRepository)
    private val declineIncomingConnectUseCase = DeclineIncomingConnectUseCase(syncRepository)
    private val switchActiveDeviceUseCase = SwitchActiveDeviceUseCase(syncRepository)
    private val sendTextUseCase = SendTextUseCase(syncRepository)
    private val sendFileUseCase = SendFileUseCase(syncRepository)
    private val disconnectActivePeerUseCase = DisconnectActivePeerUseCase(syncRepository)
    val clearAllDataUseCase = ClearAllDataUseCase(syncRepository)
    val disconnectAllUseCase = DisconnectAllUseCase(syncRepository)
    private val observeIsScanningUseCase = ObserveIsScanningUseCase(syncRepository)
    private val triggerManualScanUseCase = TriggerManualScanUseCase(syncRepository)

    init {
        // Clear RAM session state on startup asynchronously to guarantee a completely clean slate
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            clearAllDataUseCase()
        }
        startSyncUseCase()
    }

    fun syncViewModel(isDesktop: Boolean): SyncViewModel = SyncViewModel(
        isDesktop = isDesktop,
        platformOperations = platformOperations,
        observePairedDevicesUseCase = observePairedDevicesUseCase,
        observeNearbyDevicesUseCase = observeNearbyDevicesUseCase,
        observePendingIncomingConnectUseCase = observePendingIncomingConnectUseCase,
        observePendingOutgoingConnectUseCase = observePendingOutgoingConnectUseCase,
        observeConnectionStatusUseCase = observeConnectionStatusUseCase,
        observeActiveDeviceIdUseCase = observeActiveDeviceIdUseCase,
        observeConversationMessagesUseCase = observeConversationMessagesUseCase,
        observeIsScanningUseCase = observeIsScanningUseCase,
        triggerManualScanUseCase = triggerManualScanUseCase,
        requestConnectUseCase = requestConnectUseCase,
        confirmOutgoingConnectUseCase = confirmOutgoingConnectUseCase,
        dismissOutgoingConnectUseCase = dismissOutgoingConnectUseCase,
        acceptIncomingConnectUseCase = acceptIncomingConnectUseCase,
        declineIncomingConnectUseCase = declineIncomingConnectUseCase,
        switchActiveDeviceUseCase = switchActiveDeviceUseCase,
        sendTextUseCase = sendTextUseCase,
        sendFileUseCase = sendFileUseCase,
        disconnectActivePeerUseCase = disconnectActivePeerUseCase,
        disconnectAllUseCase = disconnectAllUseCase,
        localIpAddress = platformOperations.getLocalIpAddress()
    )

    suspend fun onAppExit() {
        disconnectAllUseCase()
        clearAllDataUseCase()
    }
}

expect fun createPlatformOperations(context: Any?): PlatformOperations
