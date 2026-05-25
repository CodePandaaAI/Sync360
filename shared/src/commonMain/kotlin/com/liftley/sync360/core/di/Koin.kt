package com.liftley.sync360.core.di

import com.liftley.sync360.features.sync.data.repository.SyncRepositoryImpl
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.usecase.ClearAllDataUseCase
import com.liftley.sync360.features.sync.domain.usecase.DisconnectAllUseCase
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import com.liftley.sync360.core.platform.PlatformOperations
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

expect fun platformModule(): Module

val commonModule = module {
    single<SyncRepository> {
        SyncRepositoryImpl(
            discoveryService = get(),
            localDevice = get(),
            incomingNotifier = get(),
            localLanIp = get<PlatformOperations>().getLocalIpAddress(),
            platformOperations = get()
        )
    }

    factory { ClearAllDataUseCase(get()) }
    factory { DisconnectAllUseCase(get()) }

    factory { (isDesktop: Boolean) ->
        SyncViewModel(
            isDesktop = isDesktop,
            repository = get(),
            platformOperations = get(),
            localIpAddress = get<PlatformOperations>().getLocalIpAddress()
        )
    }
}

fun initKoin(appDeclaration: (org.koin.core.KoinApplication.() -> Unit)? = null) = startKoin {
    appDeclaration?.invoke(this)
    modules(platformModule(), commonModule)
}
