package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.LookupNames
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing

internal class CatalogActions(
    val custom: (AttributeSetup, () -> Unit) -> Unit,
    val promote: (String, String) -> Unit,
    val retire: (String) -> Unit,
    val retireOption: (String) -> Unit,
    val required: (String, Boolean) -> Unit,
    val replace: (String, AttributeSetup, () -> Unit) -> Unit,
)

@Composable
internal fun CatalogManagement(shop: ShopSnapshot, product: Product, busy: Boolean, error: ShopError?, onChange: () -> Unit, actions: CatalogActions) {
    ShopPageHeader(product.title, "Product-only custom fields.")
    ShopSection(stringResource(R.string.catalog_custom_fields)) {
        Text(
            "Shared category fields and their options are managed in More. These fields belong only to ${product.title}.",
            style = MaterialTheme.typography.bodyMedium
        )
        var creating by rememberSaveable { mutableStateOf(false) }
        ShopSecondaryButton(onClick = { creating = !creating }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.catalog_add_custom_field)) }
        if (creating) FieldCreator(product.categoryId, busy, error?.takeIf { it.definitionId == null }, onChange) { setup -> actions.custom(setup) { creating = false } }
    }
    var search by rememberSaveable { mutableStateOf("") }
    ShopSearchField(search, { search = it }, "Find definition", enabled = !busy)
    shop.attributes.filter {
        it.productId == product.id && LookupNames.key(it.name).contains(LookupNames.key(search))
    }.forEach { field ->
        key(field.id) {
            ShopSection(field.name) {
                Text((if (field.productId == null) "Category field" else "Product custom field") + " · " + field.type.label + if (field.retired) " · retired" else "", style = MaterialTheme.typography.bodyMedium)
                if (!field.retired) {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    var replacing by rememberSaveable { mutableStateOf(false) }
                    ShopSecondaryButton(onClick = { expanded = !expanded; onChange() }, enabled = !busy,
                        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(if (field.productId == null) "Manage ${field.name}" else "Manage custom ${field.name}") }
                    if (expanded) {
                        ShopSecondaryButton(onClick = { actions.required(field.id, !field.required) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(if (field.required) "Make optional" else "Make required") }
                        ShopSecondaryButton(onClick = { actions.retire(field.id) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.catalog_retire_field)) }
                        if (field.productId != null && !replacing) {
                            var name by rememberSaveable { mutableStateOf(field.name) }
                            val nameError = error?.takeIf { it.definitionId == field.id && it.field == ProductField.DefinitionName }
                            ShopTextField(
                                name,
                                { name = it; onChange() },
                                stringResource(R.string.catalog_field_name_label),
                                enabled = !busy,
                                error = nameError?.message
                            )
                            Text(stringResource(R.string.catalog_promotion_note), style = MaterialTheme.typography.bodyMedium)
                            ShopPrimaryButton(stringResource(R.string.catalog_promote_to_category), enabled = !busy) { actions.promote(field.id, name) }
                        }
                        val options = shop.options.filter { it.definitionId == field.id }
                        if (options.isNotEmpty()) ShopListGroup(showDividers = true) {
                            options.forEachIndexed { index, option ->
                                ShopListRow(
                                    option.name + if (option.retired) " (retired)" else "",
                                    supporting = if (option.retired) "Retired option" else "Tap to retire this option",
                                    enabled = !busy && !option.retired,
                                    onClick = if (option.retired) null else ({
                                        actions.retireOption(
                                            option.id
                                        )
                                    }),
                                    dividerAbove = index > 0,
                                    minHeight = 56.dp
                                )
                            }
                        }
                        ShopSecondaryButton(onClick = { replacing = !replacing; onChange() }, enabled = !busy,
                            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.catalog_replace_field_type)) }
                        if (replacing) {
                            Text(stringResource(R.string.catalog_replace_note), style = MaterialTheme.typography.bodyMedium)
                            FieldCreator(product.categoryId, busy, error?.takeIf { it.definitionId == field.id }, onChange) { setup -> actions.replace(field.id, setup) { replacing = false } }
                        }
                    }
                }
            }
        }
    }
}
