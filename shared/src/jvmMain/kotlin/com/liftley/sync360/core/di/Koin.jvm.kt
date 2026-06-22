package com.liftley.sync360.core.di

import com.liftley.sync360.core.platform.DesktopPlatformOperations
import com.liftley.sync360.core.platform.IncomingMessageNotifier
import com.liftley.sync360.core.platform.NoOpIncomingMessageNotifier
import com.liftley.sync360.core.platform.PlatformOperations
import com.liftley.sync360.features.sync.domain.model.DeviceProfile
import com.liftley.sync360.features.sync.domain.model.createLocalDeviceProfile
import com.liftley.sync360.features.sync.data.network.DesktopLocalPeerDiscovery
import com.liftley.sync360.features.sync.domain.network.LocalPeerDiscovery
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<PlatformOperations> { DesktopPlatformOperations() }
    single<LocalPeerDiscovery> { DesktopLocalPeerDiscovery() }
    single<IncomingMessageNotifier> { NoOpIncomingMessageNotifier() }
    single<DeviceProfile> {
        createLocalDeviceProfile(
            context = null,
            isDesktop = true,
            desktopAddress = get<PlatformOperations>().getNetworkEnvironment().preferredAddress
        )
    }
}
