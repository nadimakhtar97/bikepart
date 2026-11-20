package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ThresholdForm(product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, save: (String) -> Unit) {
    var pieces by rememberSaveable(product.id, product.lowStockThreshold) { mutableStateOf(product.lowStockThreshold?.toString().orEmpty()) }
    val focusError = remember { BringIntoViewRequester() }
    LaunchedEffect(error) { if (error?.field == ProductField.LowStockThreshold) focusError.bringIntoView() }
    ShopSection(stringResource(R.string.threshold_section)) {
        Text(product.lowStockThreshold?.let { "Threshold: ${indianNumber(it)} pieces" } ?: "Threshold: Not set",
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(stringResource(R.string.threshold_note), style = MaterialTheme.typography.bodyMedium)
        ShopTextField(
            pieces,
            { pieces = it; onChange() },
            stringResource(R.string.threshold_field_label),
            enabled = !busy,
            error = error?.takeIf { it.field == ProductField.LowStockThreshold }?.message,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.bringIntoViewRequester(focusError)
        )

        ShopPrimaryButton(stringResource(R.string.threshold_save), enabled = !busy, onClick = { save(pieces) })
    }
}
