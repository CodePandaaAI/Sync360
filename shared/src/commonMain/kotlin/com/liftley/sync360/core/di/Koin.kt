package com.liftley.sync360.core.di

import com.liftley.sync360.presentation.viewmodel.NavigationViewModel
import com.liftley.sync360.presentation.viewmodel.SendScreenViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule = module {
    single<SendScreenViewModel> {
        SendScreenViewModel(get())
    }
    single<NavigationViewModel> {
        NavigationViewModel()
    }
}

fun initKoin(platformModule: Module, appDeclaration: KoinAppDeclaration) {
    startKoin {
        appDeclaration()
        modules(platformModule, appModule)
    }
}