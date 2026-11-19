package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay

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
    entryProvider: (T) -> NavEntry<T>,
) {
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        modifier = modifier,
        entryProvider = entryProvider,
    )
}
