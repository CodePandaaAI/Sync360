package com.liftley.sync360.core.di

import com.liftley.sync360.features.sync.data.repository.SyncRepositoryImpl
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.usecase.ClearAllDataUseCase
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import com.liftley.sync360.features.sync.presentation.navigation.SyncNavigationViewModel
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import com.liftley.sync360.features.sync.domain.runtime.SyncRuntimeController
import com.liftley.sync360.features.sync.domain.controller.SyncTransferController
import com.liftley.sync360.features.sync.domain.controller.SyncDiscoveryController
import com.liftley.sync360.features.sync.domain.diagnostics.SyncDiagnosticLog
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.core.platform.ClipboardOperations
import com.liftley.sync360.core.platform.FileOperations
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
    single { SyncTransferController(get()) }

    factory { ClearAllDataUseCase(get()) }
    factory { DisconnectAllUseCase(get()) }
    factory { SyncNavigationViewModel() }

    single<ClipboardOperations> { get<PlatformOperations>() }
    single<FileOperations> { get<PlatformOperations>() }

    factory { (isDesktop: Boolean) ->
        SyncViewModel(
            isDesktop = isDesktop,
            repository = get(),
            runtimeController = get(),
            transferController = get(),
            clipboardOperations = get(),
            fileOperations = get(),
            localIpAddress = get<PlatformOperations>().getNetworkEnvironment().preferredAddress,
            localDeviceName = get<com.liftley.sync360.features.sync.domain.model.DeviceProfile>().name
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
