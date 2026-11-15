package com.nadim.bikepart.ui

import com.nadim.bikepart.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
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
    ShopSection(stringResource(R.string.catalog_custom_fields)) {
        Text(stringResource(R.string.custom_fields_belong_only_to_promote_a, product.title), style = MaterialTheme.typography.bodyMedium)
        var creating by rememberSaveable { mutableStateOf(false) }
        ShopSecondaryButton(onClick = { creating = !creating }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.catalog_add_custom_field)) }
        if (creating) FieldCreator(product.categoryId, busy, error?.takeIf { it.definitionId == null }, onChange) { setup -> actions.custom(setup) { creating = false } }
    }
    var search by rememberSaveable { mutableStateOf("") }
    ShopSearchField(search, { search = it }, "Find definition", enabled = !busy)
    shop.attributes.filter { it.appliesTo(product.categoryId, product.id) && LookupNames.key(it.name).contains(LookupNames.key(search)) }.forEach { field ->
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
                            OutlinedTextField(name, { name = it; onChange() }, enabled = !busy, label = { Text(stringResource(R.string.catalog_field_name_label)) },
                                isError = nameError != null, supportingText = nameError?.let { ({ FormError(it.message) }) }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.catalog_promotion_note), style = MaterialTheme.typography.bodyMedium)
                            ShopPrimaryButton(stringResource(R.string.catalog_promote_to_category), enabled = !busy) { actions.promote(field.id, name) }
                        }
                        shop.options.filter { it.definitionId == field.id }.forEach { option ->
                            if (option.retired) Text(option.name + " (retired)", style = MaterialTheme.typography.bodyMedium)
                            else ShopSecondaryButton(onClick = { actions.retireOption(option.id) }, enabled = !busy,
                                modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.retire_option, option.name)) }
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
