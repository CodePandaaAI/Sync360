package com.liftley.sync360.core.di

import com.liftley.sync360.data.file.DownloadsWriter
import com.liftley.sync360.data.file.JvmDownloadsFolderOpener
import com.liftley.sync360.data.file.JvmDownloadsWriter
import com.liftley.sync360.data.file.JvmSelectedFileReader
import com.liftley.sync360.data.file.SelectedFileReader
import com.liftley.sync360.data.local.JvmClipboardProvider
import com.liftley.sync360.data.local.JvmLocalDeviceIdentityStore
import com.liftley.sync360.data.local.JvmLocalDeviceInfoProvider
import com.liftley.sync360.data.network.discovery.JvmNetworkServices
import com.liftley.sync360.data.network.tcp.FileTransferReceiver
import com.liftley.sync360.data.network.tcp.FileTransferSender
import com.liftley.sync360.data.network.tcp.JvmFileTransferReceiver
import com.liftley.sync360.data.network.tcp.JvmFileTransferSender
import com.liftley.sync360.domain.local.LocalDeviceIdentityStore
import com.liftley.sync360.domain.local.LocalDeviceInfoProvider
import com.liftley.sync360.domain.repository.ClipboardProvider
import com.liftley.sync360.domain.repository.DownloadsFolderOpener
import com.liftley.sync360.domain.service.NetworkServices
import org.koin.dsl.module
import java.io.InputStream

val jvmModule = module {
    single<ClipboardProvider> { JvmClipboardProvider() }
    single<DownloadsFolderOpener> { JvmDownloadsFolderOpener() }
    single<DownloadsWriter<InputStream>> { JvmDownloadsWriter() }
    single<SelectedFileReader> { JvmSelectedFileReader() }
    single<FileTransferSender> { JvmFileTransferSender() }
    single<FileTransferReceiver> { JvmFileTransferReceiver(get()) }

    single<LocalDeviceIdentityStore> { JvmLocalDeviceIdentityStore() }
    single<LocalDeviceInfoProvider> {
        JvmLocalDeviceInfoProvider(
            deviceUuid = get<LocalDeviceIdentityStore>().getOrCreateDeviceUuid()
        )
    }
    single<NetworkServices> { JvmNetworkServices(get()) }
}
