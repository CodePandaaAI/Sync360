package com.liftley.sync360.presentation.navigation

sealed interface NavScreen {
    data object SendScreen: NavScreen
    data object ReceiveScreen: NavScreen
    data object SettingsScreen: NavScreen
}
