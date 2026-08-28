package com.liftley.sync360.core.di

import com.liftley.sync360.core.platform.IosViewControllerProvider
import com.liftley.sync360.data.file.IosDocumentsStorage
import com.liftley.sync360.data.file.IosDownloadsFolderOpener
import com.liftley.sync360.data.file.IosSelectedFileReader
import com.liftley.sync360.data.file.SelectedFileReader
import com.liftley.sync360.data.local.IosClipboardProvider
import com.liftley.sync360.data.local.IosLocalDeviceIdentityStore
import com.liftley.sync360.data.local.IosLocalDeviceInfoProvider
import com.liftley.sync360.data.network.discovery.IosNetworkServices
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.data.network.tcp.FileTransmitter
import com.liftley.sync360.data.network.tcp.IosFileTransferReceiver
import com.liftley.sync360.data.network.tcp.IosFileTransmitter
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.repository.ClipboardProvider
import com.liftley.sync360.domain.repository.DownloadsFolderOpener
import com.liftley.sync360.domain.service.NetworkServices
import org.koin.dsl.module

val iosModule = module {
    single<ClipboardProvider> { IosClipboardProvider() }
    single { IosViewControllerProvider() }
    single<DownloadsFolderOpener> { IosDownloadsFolderOpener(get()) }
    single<SelectedFileReader> { IosSelectedFileReader() }
    single { IosDocumentsStorage() }
    single<FileTransmitter> { IosFileTransmitter() }
    single<FileTransferReceiver> { IosFileTransferReceiver(get()) }

    single<LocalDeviceIdentityStore> { IosLocalDeviceIdentityStore() }
    single<LocalDeviceInfoProvider> {
        IosLocalDeviceInfoProvider(
            deviceUuid = get<LocalDeviceIdentityStore>().getOrCreateDeviceUuid()
        )
    }
    single<NetworkServices> { IosNetworkServices(get()) }
}
