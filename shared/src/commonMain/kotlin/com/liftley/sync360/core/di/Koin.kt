package com.liftley.sync360.core.di

import com.liftley.sync360.data.NetworkServicesController
import com.liftley.sync360.data.network.http.client.Sync360HttpClient
import com.liftley.sync360.data.network.http.server.Sync360HttpServer
import com.liftley.sync360.data.IncomingServerRequestsController
import com.liftley.sync360.data.OutgoingRequestsController
import com.liftley.sync360.presentation.navigation.NavigationViewModel
import com.liftley.sync360.presentation.receive.ReceiveScreenViewModel
import com.liftley.sync360.presentation.send.SendScreenViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule = module {
    single<ReceiveScreenViewModel> { ReceiveScreenViewModel(get(), get()) }
    single<SendScreenViewModel> {
        SendScreenViewModel(get(), get())
    }
    single<NavigationViewModel> {
        NavigationViewModel()
    }

    single<Sync360HttpClient> { Sync360HttpClient() }
    single<Sync360HttpServer> {
        Sync360HttpServer(
            get()
        )
    }

    single<IncomingServerRequestsController> { IncomingServerRequestsController() }
    single<OutgoingRequestsController> {
        OutgoingRequestsController(
            get(),
            get()
        )
    }
    single<NetworkServicesController> { NetworkServicesController(get(), get()) }
}

fun initKoin(platformModule: Module, appDeclaration: KoinAppDeclaration) {
    startKoin {
        appDeclaration()
        modules(platformModule, appModule)
    }
}