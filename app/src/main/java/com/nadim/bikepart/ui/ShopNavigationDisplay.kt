package com.nadim.bikepart.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

// Keep destination changes subtle, including predictive back progress.
private val shopNavigationTransition = EnterTransition.None togetherWith ExitTransition.None

/**
 * Owns the Navigation 3 display boundary for the shop.
 *
 * Route selection and app state remain supplied by the caller so the display
 * can stay independent from the shop's business actions.
 */
@Composable
internal fun <T : Any> ShopNavigationDisplay(
    backStack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // Navigation 3 caches entries by back stack. Read the latest content when an entry renders.
    val currentContent = rememberUpdatedState(content)
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        modifier = modifier,
        entryProvider = { key -> NavEntry(key) { currentContent.value(it) } },
        transitionSpec = { shopNavigationTransition },
        popTransitionSpec = { shopNavigationTransition },
        predictivePopTransitionSpec = { _ -> shopNavigationTransition },
    )
}
