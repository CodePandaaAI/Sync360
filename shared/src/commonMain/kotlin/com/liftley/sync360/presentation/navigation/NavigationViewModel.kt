package com.liftley.sync360.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {

    private val _backstack = mutableStateListOf<NavScreen>(
        NavScreen.SendScreen
    )

    val backstack: List<NavScreen> = _backstack

    fun navigateToSend() {
        _backstack.remove(NavScreen.SettingsScreen)
        _backstack.remove(NavScreen.ReceiveScreen)
    }

    fun navigateToReceive() {
        _backstack.remove(NavScreen.SettingsScreen)

        if (_backstack.lastOrNull() != NavScreen.ReceiveScreen) {
            _backstack.add(NavScreen.ReceiveScreen)
        }
    }

    fun navigateToSettings() {
        if (_backstack.lastOrNull() != NavScreen.SettingsScreen) {
            _backstack.add(NavScreen.SettingsScreen)
        }
    }

    fun goBack() {
        if (_backstack.lastOrNull() != NavScreen.SendScreen) {
            _backstack.removeLastOrNull()
        }
    }

    fun setTwoPane(enabled: Boolean) {
        val settingsWasOpen =
            _backstack.lastOrNull() == NavScreen.SettingsScreen

        if (settingsWasOpen) {
            _backstack.removeLast()
        }

        if (enabled) {
            if (NavScreen.ReceiveScreen !in _backstack) {
                _backstack.add(NavScreen.ReceiveScreen)
            }
        } else {
            _backstack.remove(NavScreen.ReceiveScreen)
        }

        if (settingsWasOpen) {
            _backstack.add(NavScreen.SettingsScreen)
        }
    }

    fun currentScreen(): NavScreen {
        return _backstack.last()
    }
}