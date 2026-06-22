package com.liftley.sync360.core.di

import android.content.Context
import com.liftley.sync360.core.platform.AndroidPlatformOperations
import com.liftley.sync360.core.platform.AndroidIncomingMessageNotifier
import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.createLocalDeviceProfile
import com.liftley.sync360.features.sync.data.network.AndroidLocalPeerDiscovery
import com.liftley.sync360.features.sync.domain.network.LocalPeerDiscovery
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<PlatformOperations> { AndroidPlatformOperations(get<Context>()) }
    single<LocalPeerDiscovery> { AndroidLocalPeerDiscovery(get<Context>()) }
    single<IncomingMessageNotifier> { AndroidIncomingMessageNotifier(get<Context>()) }
    single<DeviceProfile> {
        createLocalDeviceProfile(
            context = get<Context>(),
            isDesktop = false,
            desktopAddress = get<PlatformOperations>().getNetworkEnvironment().preferredAddress
        )
    }
}
