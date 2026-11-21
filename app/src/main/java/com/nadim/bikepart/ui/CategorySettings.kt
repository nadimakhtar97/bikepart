package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource

@Composable
internal fun CategorySettings(
    shop: ShopSnapshot,
    busy: Boolean,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    ShopPageHeader("Categories", "Open a category to manage its shared fields and choice options.")
    ShopSecondaryButton(
        onClick = { creating = true },
        enabled = !busy,
        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
    ) { Text(stringResource(R.string.category_add)) }
    if (creating) LookupNameDialog(
        title = stringResource(R.string.category_create_title),
        fieldLabel = stringResource(R.string.category_name_label),
        createLabel = stringResource(R.string.category_add),
        busy = busy,
        onDismiss = { creating = false },
        onCreate = { onCreate(it); creating = false },
    )
    ShopSectionHeader(stringResource(R.string.more_catalog))
    ShopListGroup {
        shop.categories.forEach { category ->
            key(category.id) {
                var editing by rememberSaveable(category.id) { mutableStateOf(false) }
                var name by rememberSaveable(category.name) { mutableStateOf(category.name) }
                val productCount = shop.products.count { it.categoryId == category.id && !it.archived }
                val fieldCount =
                    shop.attributes.count { it.categoryId == category.id && it.productId == null && !it.retired }
                ShopListRow(
                    category.name,
                    supporting = "$productCount active products · $fieldCount shared fields",
                    icon = R.drawable.ic_category,
                    trailing = {
                        IconButton(onClick = { editing = true }, enabled = !busy) {
                            Icon(
                                painterResource(R.drawable.ic_edit),
                                contentDescription = "Rename ${category.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    showChevron = true,
                    enabled = !busy,
                    actionLabel = "Manage fields for ${category.name}",
                    onClick = { onOpen(category.id) },
                )
                if (editing) AlertDialog(
                    onDismissRequest = { if (!busy) editing = false },
                    title = { Text(stringResource(R.string.rename, category.name)) },
                    text = {
                        ShopTextField(
                            name,
                            { name = it },
                            stringResource(R.string.category_name_label),
                            enabled = !busy
                        )
                    },
                    confirmButton = {
                        TextButton(enabled = !busy && name != category.name, onClick = {
                            editing = false
                            onRename(category.id, name)
                        }) { Text(stringResource(R.string.save, category.name)) }
                    },
                    dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}
