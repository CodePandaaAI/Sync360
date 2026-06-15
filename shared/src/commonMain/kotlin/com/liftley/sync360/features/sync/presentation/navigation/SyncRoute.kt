package com.liftley.sync360.features.sync.presentation.navigation

sealed interface SyncRoute {
    data object Home : SyncRoute
}
