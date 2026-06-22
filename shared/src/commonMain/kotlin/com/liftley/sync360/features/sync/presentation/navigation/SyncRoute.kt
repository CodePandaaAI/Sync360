package com.liftley.sync360.features.sync.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface SyncRoute : NavKey {
    @Serializable
    data object Send : SyncRoute

    @Serializable
    data object Receive : SyncRoute

    @Serializable
    data object Settings : SyncRoute
}
