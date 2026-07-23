package com.liftley.sync360.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {
    val backstack: SnapshotStateList<NavScreen> = mutableStateListOf(
        NavScreen.ReceiveScreen,
        NavScreen.SendScreen
    )

    fun addScreen(screen: NavScreen) {
        if (checkCurrentTop() == screen) return

        backstack.remove(screen)
        backstack.add(screen)
    }

    fun removeLast() {
        when (checkCurrentTop()) {
            NavScreen.SettingsScreen -> backstack.removeLast()
            NavScreen.ReceiveScreen -> addScreen(NavScreen.SendScreen)
            NavScreen.SendScreen -> Unit
        }
    }

    fun checkCurrentTop(): NavScreen {
        return backstack.last()
    }

    fun removeAllExceptAddScreen() {
        addScreen(NavScreen.SendScreen)
    }
}
