package com.liftley.sync360.presentation.featureNavigation

sealed interface NavScreen {
    data object SendScreen: NavScreen
    data object ReceiveScreen: NavScreen
}