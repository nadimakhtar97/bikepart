package com.nadim.bikepart.ui

import androidx.compose.runtime.Composable
import com.nadim.bikepart.shop.LocalCapture
import com.nadim.bikepart.shop.ShopSnapshot

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
    com.microsoft.fluentui.compose.Scaffold(
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
