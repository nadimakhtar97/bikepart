package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.BarcodeTarget
import com.nadim.bikepart.shop.Cart
import com.nadim.bikepart.shop.CatalogDraft
import com.nadim.bikepart.shop.ShopSnapshot

@Composable
internal fun ScanRoute(
    shop: ShopSnapshot,
    purpose: ScanPurpose,
    busy: Boolean,
    error: ShopError?,
    camera: ScanCameraContent,
    manual: () -> Unit,
    onChange: () -> Unit,
    add: (String, String) -> Unit,
    receive: (String) -> Unit,
    open: (String) -> Unit,
    copy: (CatalogDraft) -> Unit,
    cart: Cart?,
    link: (String, BarcodeTarget, Long?, () -> Unit) -> Unit,
) {
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title = stringResource(R.string.title_scan_barcode), showBack = true, enabled = !busy) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            ContextualScanner(
                shop = shop, purpose = purpose, busy = busy, error = error,
                camera = camera, manual = manual, onChange = onChange, add = add,
                receive = receive, open = open, copy = copy, cart = cart, link = link,
            )
        }
    }
}
