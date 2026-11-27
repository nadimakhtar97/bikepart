package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.tokenized.notification.NotificationDuration
import com.microsoft.fluentui.tokenized.notification.Snackbar
import com.microsoft.fluentui.tokenized.notification.SnackbarState
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun ShopEmptyState(title: String, description: String, icon: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = ShopSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(ShopSpacing.content).size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp))
    }
}

private val LocalErrorSnackbar = compositionLocalOf<SnackbarState?> { null }

@Composable
internal fun BoxScope.ErrorSnackbarHost(screen: Any = Unit, content: @Composable () -> Unit) {
    val state = remember(screen) { SnackbarState() }
    CompositionLocalProvider(LocalErrorSnackbar provides state, content = content)
    Snackbar(
        snackbarState = state,
        enableSwipeToDismiss = false,
        modifier = Modifier.align(Alignment.BottomCenter)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(ShopSpacing.content)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun ErrorSnackbar(message: String?, blocking: Boolean = false, attempt: Any? = message) {
    val state = LocalErrorSnackbar.current ?: return
    LaunchedEffect(state, attempt, message, blocking) {
        if (!message.isNullOrBlank()) {
            state.showSnackbar(
                message = message,
                enableDismiss = !blocking,
                duration = if (blocking) NotificationDuration.INDEFINITE else NotificationDuration.LONG,
            )
        }
    }
}
