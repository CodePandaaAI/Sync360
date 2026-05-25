package com.liftley.sync360.core.di

import com.liftley.sync360.core.database.DatabaseDriverFactory
import com.liftley.sync360.core.database.SyncDatabase
import com.liftley.sync360.features.sync.data.network.KtorSyncNetworkService
import com.liftley.sync360.features.sync.data.repository.SyncRepositoryImpl
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.DeviceType
import com.liftley.sync360.features.sync.domain.network.NetworkDiscoveryService
import com.liftley.sync360.features.sync.domain.network.SyncNetworkService
import com.liftley.sync360.features.sync.domain.repository.SyncRepository
import com.liftley.sync360.features.sync.domain.usecase.*
import com.liftley.sync360.features.sync.presentation.SyncViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val commonModule = module {
    
    // Local Device Profile Stub
    single { 
        DeviceProfile(
            id = "device-12345",
            name = "My Device",
            type = DeviceType.DESKTOP,
            isOnline = true
        )
    }

    // Database
    single { SyncDatabase(get<DatabaseDriverFactory>().createDriver()) }

    // Network & Repository
    single<SyncNetworkService> { KtorSyncNetworkService() }
    
    // Dummy discovery service until implemented
    single<NetworkDiscoveryService> { 
        object : NetworkDiscoveryService {
            override val discoveredDevices = kotlinx.coroutines.flow.MutableStateFlow(emptyList<DeviceProfile>())
            override fun startDiscovery() {}
            override fun stopDiscovery() {}
            override fun registerHost(port: Int, deviceId: String, deviceName: String, deviceType: String) {}
        }
    }
    
    single<SyncRepository> { 
        SyncRepositoryImpl(
            networkService = get(),
            discoveryService = get(),
            database = get(),
            localDevice = get()
        )
    }

    // UseCases
    factory { ObserveDevicesUseCase(get()) }
    factory { ObserveConnectionStatusUseCase(get()) }
    factory { ObserveActiveDeviceIdUseCase(get()) }
    factory { ObserveRecentPayloadsUseCase(get()) }
    factory { StartDiscoveryUseCase(get()) }
    factory { ConnectToDeviceUseCase(get()) }
    factory { SendTextUseCase(get()) }
    factory { DisconnectAllUseCase(get()) }

    // ViewModel
    factory { (isDesktop: Boolean) ->
        SyncViewModel(
            isDesktop = isDesktop,
            observeDevicesUseCase = get(),
            observeConnectionStatusUseCase = get(),
            observeActiveDeviceIdUseCase = get(),
            observeRecentPayloadsUseCase = get(),
            startDiscoveryUseCase = get(),
            connectToDeviceUseCase = get(),
            sendTextUseCase = get(),
            disconnectAllUseCase = get()
        )
    }
}
