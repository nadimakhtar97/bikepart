package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun StockScreen(shop: ShopSnapshot, onAdd: () -> Unit, onOpen: (String) -> Unit, loadPhoto: PhotoLoader, initialHealth: String? = null, busy: Boolean = false,
    onScan: () -> Unit = {}, onProductAction: (Product, String) -> Unit = { _, _ -> }, initialQuery: String = "") {
    if (shop.products.none { !it.archived }) {
        if (shop.products.isEmpty()) ShopEmptyState(stringResource(R.string.stock_no_products_title), "Add a product to start tracking your stock.", R.drawable.ic_stock)
        else ShopEmptyState(stringResource(R.string.home_no_active_title), "Add a product for current work. Archived products and their history are available under More.", R.drawable.ic_stock)
    } else {
        ShopPageHeader(stringResource(R.string.stock_find_product))
        StockSearch(shop, initialHealth, loadPhoto, onOpen, busy, onScan, onProductAction, initialQuery)
    }
}
