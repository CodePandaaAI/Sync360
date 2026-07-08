package com.liftley.sync360.core.di

import com.liftley.sync360.data.local.AndroidClipboardProvider
import com.liftley.sync360.data.local.AndroidLocalDeviceIdentityStore
import com.liftley.sync360.data.local.AndroidLocalDeviceInfoProvider
import com.liftley.sync360.data.network.discovery.AndroidNetworkServices
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.repository.AndroidFilesManager
import com.liftley.sync360.domain.repository.ClipboardProvider
import com.liftley.sync360.domain.repository.FilesManager
import com.liftley.sync360.domain.service.NetworkServices
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


val androidModule = module {
    single<ClipboardProvider> { AndroidClipboardProvider(androidContext()) }
    single<NetworkServices> { AndroidNetworkServices(context = androidContext(), get()) }
    single<FilesManager> { AndroidFilesManager(androidContext()) }

    factory<LocalDeviceIdentityStore> { AndroidLocalDeviceIdentityStore(context = androidContext()) }
    factory<LocalDeviceInfoProvider> { AndroidLocalDeviceInfoProvider(deviceUuid = get<LocalDeviceIdentityStore>().getOrCreateDeviceUuid()) }
}