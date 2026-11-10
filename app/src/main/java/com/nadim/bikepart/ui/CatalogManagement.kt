package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    ShopSection("Product custom fields") {
        Text("Custom fields belong only to ${product.title}. Promote a field explicitly to share it with this category.", style = MaterialTheme.typography.bodyMedium)
        var creating by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(onClick = { creating = !creating }, enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Add product custom field") }
        if (creating) FieldCreator(product.categoryId, busy, error?.takeIf { it.definitionId == null }, onChange) { setup -> actions.custom(setup) { creating = false } }
    }
    var search by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(search, { search = it }, enabled = !busy, label = { Text("Find definition") }, modifier = Modifier.fillMaxWidth())
    shop.attributes.filter { it.appliesTo(product.categoryId, product.id) && LookupNames.key(it.name).contains(LookupNames.key(search)) }.forEach { field ->
        key(field.id) {
            ShopSection(field.name) {
                Text((if (field.productId == null) "Category field" else "Product custom field") + " · " + field.type.label + if (field.retired) " · retired" else "", style = MaterialTheme.typography.bodyMedium)
                if (!field.retired) {
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    OutlinedButton(onClick = { expanded = !expanded }, enabled = !busy,
                        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(if (field.productId == null) "Manage ${field.name}" else "Manage custom ${field.name}") }
                    if (expanded) {
                        OutlinedButton(onClick = { actions.required(field.id, !field.required) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(if (field.required) "Make optional" else "Make required") }
                        OutlinedButton(onClick = { actions.retire(field.id) }, enabled = !busy,
                            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Retire field") }
                        if (field.productId != null) {
                            var name by rememberSaveable { mutableStateOf(field.name) }
                            val nameError = error?.takeIf { it.definitionId == field.id && it.field == ProductField.DefinitionName }
                            OutlinedTextField(name, { name = it; onChange() }, enabled = !busy, label = { Text("Category field name") },
                                isError = nameError != null, supportingText = nameError?.let { ({ FormError(it.message) }) }, modifier = Modifier.fillMaxWidth())
                            Text("If the name conflicts, choose a distinct name and meaning. Promotion never merges fields.", style = MaterialTheme.typography.bodyMedium)
                            ShopPrimaryButton("Promote to category", enabled = !busy) { actions.promote(field.id, name) }
                        }
                        shop.options.filter { it.definitionId == field.id }.forEach { option ->
                            if (option.retired) Text(option.name + " (retired)", style = MaterialTheme.typography.bodyMedium)
                            else OutlinedButton(onClick = { actions.retireOption(option.id) }, enabled = !busy,
                                modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Retire option: ${option.name}") }
                        }
                        var replacing by rememberSaveable { mutableStateOf(false) }
                        OutlinedButton(onClick = { replacing = !replacing }, enabled = !busy,
                            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Replace field type") }
                        if (replacing) {
                            Text("Use a distinct name and a new type. The original field is retired and its values remain unchanged.", style = MaterialTheme.typography.bodyMedium)
                            FieldCreator(product.categoryId, busy, error?.takeIf { it.definitionId == field.id }, onChange) { setup -> actions.replace(field.id, setup) { replacing = false } }
                        }
                    }
                }
            }
        }
    }
}
