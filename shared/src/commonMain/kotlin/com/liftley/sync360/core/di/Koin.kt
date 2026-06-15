package com.liftley.sync360.core.di

import com.liftley.sync360.features.sync.data.repository.SyncRepositoryImpl
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.usecase.ClearAllDataUseCase
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import com.liftley.sync360.features.sync.presentation.navigation.SyncNavigationViewModel
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import com.liftley.sync360.features.sync.domain.runtime.SyncRuntimeController
import com.liftley.sync360.features.sync.domain.controller.SyncConnectionController
import com.liftley.sync360.features.sync.domain.controller.SyncTransferController
import com.liftley.sync360.features.sync.domain.controller.SyncDiscoveryController
import com.liftley.sync360.features.sync.domain.diagnostics.SyncDiagnosticLog
import com.liftley.sync360.core.platform.PlatformOperations
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect fun platformModule(): Module

val commonModule = module {
    single<SyncRepository> {
        SyncRepositoryImpl(
            discoveryController = get(),
            localDevice = get(),
            incomingNotifier = get(),
            platformOperations = get()
        )
    }
    single { SyncDiscoveryController(get(), get(), get()) }
    single { SyncDiagnosticLog() }
    single { SyncRuntimeController(get(), get(), get(), get()) }
    single { SyncConnectionController(get()) }
    single { SyncTransferController(get()) }

    factory { ClearAllDataUseCase(get()) }
    factory { DisconnectAllUseCase(get()) }
    factory { SyncNavigationViewModel() }

    factory { (isDesktop: Boolean) ->
        SyncViewModel(
            isDesktop = isDesktop,
            repository = get(),
            runtimeController = get(),
            connectionController = get(),
            transferController = get(),
            platformOperations = get(),
            localIpAddress = get<PlatformOperations>().getNetworkEnvironment().preferredAddress
        )
    }
}

fun initKoin(appDeclaration: (org.koin.core.KoinApplication.() -> Unit)? = null) =
    startKoin {
        appDeclaration?.invoke(this)
        modules(platformModule(), commonModule)
    }.also { application ->
        application.koin.get<SyncRuntimeController>().start()
    }
