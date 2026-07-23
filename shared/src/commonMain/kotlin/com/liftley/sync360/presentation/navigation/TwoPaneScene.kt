package com.liftley.sync360.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

data class TwoPaneScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    val firstEntry: NavEntry<T>,
    val secondEntry: NavEntry<T>
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(firstEntry, secondEntry)

    override val content: @Composable () -> Unit = {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(0.5f)) {
                firstEntry.Content()
            }
            Column(modifier = Modifier.weight(0.5f)) {
                secondEntry.Content()
            }
        }
    }

    companion object {
        fun firstPane() = metadata {
            put(FirstPaneKey, true)
        }

        fun secondPane() = metadata {
            put(SecondPaneKey, true)
        }
    }

    object FirstPaneKey : NavMetadataKey<Boolean>
    object SecondPaneKey : NavMetadataKey<Boolean>
}

class TwoPaneSceneStrategy<T : Any>(
    private val windowSizeClass: WindowSizeClass
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(
        entries: List<NavEntry<T>>
    ): Scene<T>? {
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }

        val currentEntry = entries.lastOrNull() ?: return null
        if (
            TwoPaneScene.FirstPaneKey !in currentEntry.metadata &&
            TwoPaneScene.SecondPaneKey !in currentEntry.metadata
        ) {
            return null
        }

        val firstEntry = entries.findLast {
            TwoPaneScene.FirstPaneKey in it.metadata
        } ?: return null
        val secondEntry = entries.findLast {
            TwoPaneScene.SecondPaneKey in it.metadata
        } ?: return null

        return TwoPaneScene(
            key = firstEntry.contentKey to secondEntry.contentKey,
            previousEntries = entries.dropLast(1),
            firstEntry = firstEntry,
            secondEntry = secondEntry
        )
    }
}
