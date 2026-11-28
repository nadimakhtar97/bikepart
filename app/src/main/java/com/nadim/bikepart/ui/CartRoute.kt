package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.CartDraft
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.indianNumber
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopAppBarColors

@Composable
internal fun CartRoute(
    shop: ShopSnapshot,
    draft: CartDraft?,
    busy: Boolean,
    error: ShopError?,
    edit: (String?, String?, String?) -> Unit,
    pause: () -> Unit,
    addProduct: () -> Unit,
    retry: () -> Unit,
    reload: () -> Unit,
    checkout: () -> Unit,
    removeProduct: (String) -> Unit,
    discard: () -> Unit,
) {
    val cart = draft?.let { shop.carts.find { cart -> cart.id == it.cart.id } ?: it.cart }
    var confirmDiscard by rememberSaveable(cart?.id) { mutableStateOf(false) }
    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = {
            ShopRouteAppBar(
                title = cart?.let { stringResource(R.string.cart_3, it.number) } ?: stringResource(R.string.title_unfinished_bill),
                showBack = true,
                enabled = !busy,
                actions = if (draft != null) {
                    {
                        ShopOverflowMenu(
                            label = stringResource(R.string.cart_actions),
                            enabled = !busy,
                            triggerColor = shopAppBarColors().navigation,
                            actions = listOf(
                                ShopMenuAction(stringResource(R.string.cart_save_return), R.drawable.ic_check, onClick = pause),
                                ShopMenuAction(stringResource(R.string.cart_discard), R.drawable.ic_close, enabled = !draft.dirty) {
                                    confirmDiscard = true
                                },
                            ),
                        )
                    }
                } else null,
            )
        },
        bottomBar = {
            val density = LocalDensity.current
            val needsEditingSpace = density.fontScale >= 1.5f && WindowInsets.ime.getBottom(density) > 0
            if (cart != null && !needsEditingSpace) {
                CartCheckoutBar(cart.totalRupees, enabled = !busy && cart.lines.isNotEmpty(), checkout = checkout)
            }
        },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            if (draft == null || cart == null) {
                ErrorSnackbar(stringResource(R.string.cart_unavailable), blocking = true)
            } else {
                CartEditor(shop, cart, draft, busy, error, edit, addProduct, removeProduct, retry, reload)
            }
        }
    }

    if (confirmDiscard && cart != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDiscard = false },
            title = { Text(stringResource(R.string.discard, stringResource(R.string.cart_3, cart.number))) },
            text = { Text(stringResource(R.string.cart_discard_stock_note)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDiscard = false; discard() },
                    enabled = !busy,
                ) { Text(stringResource(R.string.cart_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cart_keep)) }
            },
        )
    }
}

@Composable
private fun CartCheckoutBar(totalRupees: Long, enabled: Boolean, checkout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BoxWithConstraints(
                Modifier.widthIn(max = ShopSpacing.formWidth).fillMaxWidth().padding(ShopSpacing.content),
            ) {
                val totalLabel = stringResource(R.string.saved_cart_total, indianNumber(totalRupees))
                val totalStyle = MaterialTheme.typography.titleMedium
                val density = LocalDensity.current
                val halfWidth = (maxWidth - ShopSpacing.content) / 2
                val amountWidth = rememberTextMeasurer().measure(context.getString(R.string.rupee_amount, indianNumber(totalRupees)), totalStyle, softWrap = false).size.width
                val stack = maxWidth < 320.dp || density.fontScale >= 1.5f || amountWidth > with(density) { halfWidth.toPx() }
                val total: @Composable (Modifier) -> Unit = { modifier ->
                    Text(
                        totalLabel,
                        style = totalStyle,
                        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (stack) {
                    Column(verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                        total(Modifier.fillMaxWidth())
                        ShopPrimaryButton(stringResource(R.string.cart_checkout), enabled = enabled, onClick = checkout)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShopSpacing.content),
                    ) {
                        total(Modifier.weight(1f))
                        ShopPrimaryButton(
                            stringResource(R.string.cart_checkout),
                            modifier = Modifier.weight(1f),
                            enabled = enabled,
                            onClick = checkout,
                        )
                    }
                }
            }
        }
    }
}
