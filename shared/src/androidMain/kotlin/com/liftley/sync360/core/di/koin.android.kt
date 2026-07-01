package com.liftley.sync360.core.di

import com.liftley.sync360.data.AndroidClipboardProvider
import com.liftley.sync360.data.AndroidLocalDeviceIdentityStore
import com.liftley.sync360.data.AndroidNetworkServices
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.repository.ClipboardProvider
import com.liftley.sync360.domain.repository.NetworkServices
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


val androidModule = module {
    single<ClipboardProvider> { AndroidClipboardProvider(androidContext()) }
    single<NetworkServices> { AndroidNetworkServices(context = androidContext(), get()) }
    factory<LocalDeviceIdentityStore> { AndroidLocalDeviceIdentityStore(context = androidContext()) }
}