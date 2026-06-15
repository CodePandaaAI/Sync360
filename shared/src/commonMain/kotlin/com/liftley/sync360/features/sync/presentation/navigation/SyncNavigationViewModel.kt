package com.liftley.sync360.features.sync.presentation.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncNavigationViewModel : ViewModel() {
    private val _backStack = MutableStateFlow<List<SyncRoute>>(listOf(SyncRoute.Home))
    val backStack: StateFlow<List<SyncRoute>> = _backStack.asStateFlow()

    val currentRoute: SyncRoute
        get() = _backStack.value.last()

    fun navigate(route: SyncRoute) {
        if (currentRoute == route) return
        _backStack.value = _backStack.value + route
    }

    fun pop(): Boolean {
        val current = _backStack.value
        if (current.size <= 1) return false
        _backStack.value = current.dropLast(1)
        return true
    }
}
