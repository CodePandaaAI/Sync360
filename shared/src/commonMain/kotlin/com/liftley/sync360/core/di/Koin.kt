package com.liftley.sync360.core.di

import com.liftley.sync360.features.sync.data.network.KtorSyncNetworkService
import com.liftley.sync360.features.sync.data.repository.SyncRepositoryImpl
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.usecase.*
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import com.liftley.sync360.core.platform.PlatformOperations
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect fun platformModule(): Module

val commonModule = module {
    single<SyncNetworkService> { KtorSyncNetworkService() }
    
    single<SyncRepository> {
        SyncRepositoryImpl(
            networkService = get(),
            discoveryService = get(),
            localDevice = get(),
            incomingNotifier = get(),
            localLanIp = get<PlatformOperations>().getLocalIpAddress(),
            platformOperations = get()
        )
    }

    // Use Cases
    factory { ObservePairedDevicesUseCase(get()) }
    factory { ObserveNearbyDevicesUseCase(get()) }
    factory { ObservePendingIncomingConnectUseCase(get()) }
    factory { ObservePendingOutgoingConnectUseCase(get()) }
    factory { ObserveConnectionStatusUseCase(get()) }
    factory { ObserveActiveDeviceIdUseCase(get()) }
    factory { ObserveConversationMessagesUseCase(get()) }
    factory { RequestConnectUseCase(get()) }
    factory { ConfirmOutgoingConnectUseCase(get()) }
    factory { DismissOutgoingConnectUseCase(get()) }
    factory { AcceptIncomingConnectUseCase(get()) }
    factory { DeclineIncomingConnectUseCase(get()) }
    factory { SwitchActiveDeviceUseCase(get()) }
    factory { SendTextUseCase(get()) }
    factory { SendFileUseCase(get()) }
    factory { DisconnectActivePeerUseCase(get()) }
    factory { ClearAllDataUseCase(get()) }
    factory { DisconnectAllUseCase(get()) }
    factory { ObserveIsScanningUseCase(get()) }
    factory { TriggerManualScanUseCase(get()) }
    factory { StartSyncUseCase(get()) }

    // ViewModel factory
    factory { (isDesktop: Boolean) ->
        SyncViewModel(
            isDesktop = isDesktop,
            platformOperations = get(),
            observePairedDevicesUseCase = get(),
            observeNearbyDevicesUseCase = get(),
            observePendingIncomingConnectUseCase = get(),
            observePendingOutgoingConnectUseCase = get(),
            observeConnectionStatusUseCase = get(),
            observeActiveDeviceIdUseCase = get(),
            observeConversationMessagesUseCase = get(),
            observeIsScanningUseCase = get(),
            triggerManualScanUseCase = get(),
            requestConnectUseCase = get(),
            confirmOutgoingConnectUseCase = get(),
            dismissOutgoingConnectUseCase = get(),
            acceptIncomingConnectUseCase = get(),
            declineIncomingConnectUseCase = get(),
            switchActiveDeviceUseCase = get(),
            sendTextUseCase = get(),
            sendFileUseCase = get(),
            disconnectActivePeerUseCase = get(),
            startSyncUseCase = get(),
            localIpAddress = get<PlatformOperations>().getLocalIpAddress()
        )
    }
}

fun initKoin(appDeclaration: (org.koin.core.KoinApplication.() -> Unit)? = null) = startKoin {
    appDeclaration?.invoke(this)
    modules(platformModule(), commonModule)
}
