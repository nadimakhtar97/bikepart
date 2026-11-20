package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.LocalCapture
import com.nadim.bikepart.shop.ShopSnapshot
import com.microsoft.fluentui.compose.Scaffold
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.controlTokens.*
import com.microsoft.fluentui.tokenized.AppBar
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.FloatingActionButton
import com.microsoft.fluentui.tokenized.drawer.Drawer
import com.microsoft.fluentui.tokenized.drawer.rememberDrawerState
import com.microsoft.fluentui.tokenized.listitem.ListItem
import com.microsoft.fluentui.tokenized.navigation.TabBar
import com.microsoft.fluentui.tokenized.navigation.TabData
import com.microsoft.fluentui.tokenized.notification.Badge
import com.microsoft.fluentui.tokenized.notification.NotificationResult
import com.microsoft.fluentui.tokenized.notification.Snackbar
import com.microsoft.fluentui.tokenized.notification.SnackbarState


@Composable
internal fun BackupsRoute(
    model: com.nadim.bikepart.ShopViewModel,
    shop: ShopSnapshot,
    localCapture: LocalCapture?,
    recoveryCapture: LocalCapture?,
    busy: Boolean,
    error: String?,
    saved: Boolean,
    recoveryExport: Boolean,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = "Shop backups", showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            DriveBackupControls(model, shop.revision)
            LocalBackupControls(
                localCapture, shop.revision, busy,
                error.takeUnless { recoveryExport }, saved && !recoveryExport,
                model::captureBackup, model::saveBackupCopy,
            )
            if (recoveryCapture != null) {
                LocalBackupControls(
                    recoveryCapture, shop.revision, busy,
                    error.takeIf { recoveryExport }, saved && recoveryExport,
                    model::captureBackup, model::saveBackupCopy,
                    recovery = true,
                )
            }
        }
    }
}
