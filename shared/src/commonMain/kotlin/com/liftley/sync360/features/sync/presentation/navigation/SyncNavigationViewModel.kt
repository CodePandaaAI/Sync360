package com.liftley.sync360.features.sync.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

class SyncNavigationViewModel : ViewModel() {
    val backStack: SnapshotStateList<SyncRoute> = mutableStateListOf(SyncRoute.Send)

    val currentRoute: SyncRoute
        get() = backStack.lastOrNull() ?: SyncRoute.Send

    fun navigate(route: SyncRoute) {
        if (currentRoute == route) return
        backStack.add(route)
    }

    fun navigateTopLevel(route: SyncRoute) {
        if (currentRoute == route) return
        backStack.clear()
        backStack.add(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLast()
        return true
    }
}
