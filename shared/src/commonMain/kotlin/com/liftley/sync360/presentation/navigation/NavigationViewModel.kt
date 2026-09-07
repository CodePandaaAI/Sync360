package com.liftley.sync360.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {

    private val _backstack = mutableStateListOf<NavScreen>(
        NavScreen.SendScreen
    )

    val backstack: List<NavScreen> = _backstack

    fun navigateTo(screen: NavScreen) {
        if (_backstack.last() == screen) return

        while (_backstack.size > 1) {
            _backstack.removeLastOrNull()
        }

        if (screen != NavScreen.SendScreen) {
            _backstack.add(screen)
        }
    }

    fun goBack() {
        if (_backstack.lastOrNull() != NavScreen.SendScreen) {
            _backstack.removeLastOrNull()
        }
    }

    fun currentScreen(): NavScreen {
        return _backstack.last()
    }
}