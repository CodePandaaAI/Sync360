package com.liftley.sync360.presentation.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel

class NavigationViewModel : ViewModel() {
    val backstack: SnapshotStateList<NavScreen> = mutableStateListOf(NavScreen.SendScreen)

    fun addScreen(screen: NavScreen) {
        if (checkCurrentTop() == screen) return
        backstack.add(screen)
    }

    fun removeLast() {
        if (backstack.size > 1) {
            backstack.removeAt(backstack.size - 1)
        }
    }

    fun checkCurrentTop(): NavScreen {
        return backstack.last()
    }

    fun removeAllExceptAddScreen() {
        backstack.removeAll { screen -> screen !is NavScreen.SendScreen }
    }
}