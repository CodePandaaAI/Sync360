package com.liftley.sync360.core.di

import com.liftley.sync360.data.local.AndroidClipboardProvider
import com.liftley.sync360.data.local.AndroidLocalDeviceIdentityStore
import com.liftley.sync360.data.local.AndroidLocalDeviceInfoProvider
import com.liftley.sync360.data.file.AndroidDownloadsWriter
import com.liftley.sync360.data.file.AndroidDownloadsFolderOpener
import com.liftley.sync360.data.file.AndroidSelectedFileReader
import com.liftley.sync360.data.file.DownloadsWriter
import com.liftley.sync360.data.file.SelectedFileReader
import com.liftley.sync360.data.network.discovery.AndroidNetworkServices
import com.liftley.sync360.data.network.tcp.AndroidFileTransferReceiver
import com.liftley.sync360.data.network.tcp.AndroidFileTransferSender
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.data.network.tcp.FileTransferSender
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.repository.ClipboardProvider
import com.liftley.sync360.domain.repository.DownloadsFolderOpener
import com.liftley.sync360.domain.service.NetworkServices
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.InputStream


val androidModule = module {
    single<ClipboardProvider> { AndroidClipboardProvider(androidContext()) }
    single<DownloadsFolderOpener> { AndroidDownloadsFolderOpener(androidContext()) }
    single<NetworkServices> { AndroidNetworkServices(context = androidContext(), get()) }
    single<SelectedFileReader> { AndroidSelectedFileReader(androidContext()) }
    single<DownloadsWriter<InputStream>> { AndroidDownloadsWriter(androidContext()) }

    single<FileTransferSender> {
        AndroidFileTransferSender(
            context = androidContext()
        )
    }

    single<FileTransferReceiver> {
        AndroidFileTransferReceiver(
            downloadsWriter = get()
        )
    }

    factory<LocalDeviceIdentityStore> { AndroidLocalDeviceIdentityStore(context = androidContext()) }
    factory<LocalDeviceInfoProvider> { AndroidLocalDeviceInfoProvider(deviceUuid = get<LocalDeviceIdentityStore>().getOrCreateDeviceUuid()) }
}
