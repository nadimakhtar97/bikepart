package com.nadim.bikepart.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.microsoft.fluentui.compose.Scaffold
import com.nadim.bikepart.R

@Composable
internal fun DeveloperOptionRoute(busy: Boolean, onFill: (onAdded: () -> Unit) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var added by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.developer_option)
    val action = stringResource(R.string.developer_add_dummy_data)

    Scaffold(
        modifier = ShopRouteScaffoldModifier,
        topBar = { ShopRouteAppBar(title, showBack = true, enabled = !busy) },
    ) { padding ->
        ShopRouteColumn(contentPadding = padding) {
            ShopPrimaryButton(
                label = stringResource(R.string.developer_fill_dummy_data),
                enabled = !busy,
                onClick = { confirm = true },
            )
            if (added) {
                Text(
                    stringResource(R.string.developer_dummy_data_added),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(action) },
            text = { Text(stringResource(R.string.developer_dummy_data_details)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirm = false
                        added = false
                        onFill { added = true }
                    },
                ) { Text(action) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
