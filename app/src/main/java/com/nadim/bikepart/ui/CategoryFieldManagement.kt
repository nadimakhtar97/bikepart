package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.LookupNames
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing

internal class CategoryFieldActions(
    val create: (AttributeSetup, () -> Unit) -> Unit,
    val createOption: (String, String, () -> Unit) -> Unit,
    val retire: (String) -> Unit,
    val retireOption: (String) -> Unit,
    val required: (String, Boolean) -> Unit,
    val replace: (String, AttributeSetup, () -> Unit) -> Unit,
)

@Composable
internal fun CategoryFieldManagement(
    shop: ShopSnapshot,
    categoryId: String,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    actions: CategoryFieldActions,
) {
    val category = shop.categories.firstOrNull { it.id == categoryId }
    if (category == null) {
        ShopEmptyState(
            "Category unavailable",
            "Return to Categories and choose an active category.",
            R.drawable.ic_category
        )
        return
    }

    val fields = shop.attributes.filter { it.categoryId == categoryId && it.productId == null }
    ShopPageHeader(category.name, "Shared fields and choice options for this category.")
    ShopSectionHeader(
        "Category fields",
        supporting = if (fields.isEmpty()) "No shared fields yet" else "${fields.size} shared ${if (fields.size == 1) "field" else "fields"}",
    )

    var creating by rememberSaveable(categoryId) { mutableStateOf(false) }
    ShopSecondaryButton(
        onClick = { creating = !creating },
        enabled = !busy,
        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
    ) { Text(if (creating) "Cancel" else "Add category field") }
    if (creating) {
        ShopSection("New category field") {
            FieldCreator(
                categoryId,
                busy,
                error?.takeIf { it.definitionId == null },
                onChange
            ) { setup ->
                actions.create(setup) { creating = false }
            }
        }
    }

    if (fields.isEmpty() && !creating) {
        ShopEmptyState(
            "No category fields",
            "Add a field to describe products in ${category.name}.",
            R.drawable.ic_category
        )
    }

    fields.forEach { field ->
        key(field.id) {
            ShopSection(field.name) {
                Text(
                    (if (field.required) "Required" else "Optional") + " · " + field.type.label +
                            if (field.unit.isBlank()) "" else " · ${field.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (field.retired) {
                    Text(
                        "Retired · retained for existing product details",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    var replacing by rememberSaveable(field.id) { mutableStateOf(false) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(ShopSpacing.small),
                    ) {
                        ShopSecondaryButton(
                            onClick = { actions.required(field.id, !field.required) },
                            enabled = !busy,
                        ) { Text(if (field.required) "Make optional" else "Make required") }
                        ShopSecondaryButton(
                            onClick = { actions.retire(field.id) },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.catalog_retire_field)) }
                        ShopSecondaryButton(
                            onClick = { replacing = !replacing; onChange() },
                            enabled = !busy,
                        ) { Text(if (replacing) "Cancel replacement" else stringResource(R.string.catalog_replace_field_type)) }
                    }
                    if (replacing) {
                        Text(
                            stringResource(R.string.catalog_replace_note),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FieldCreator(
                            categoryId,
                            busy,
                            error?.takeIf { it.definitionId == field.id },
                            onChange
                        ) { setup ->
                            actions.replace(field.id, setup) { replacing = false }
                        }
                    }
                }

                if (field.type.isChoice) {
                    ShopSectionHeader(
                        "Options",
                        supporting = "Options belong to ${field.name}.",
                    )
                    val options = shop.options.filter { it.definitionId == field.id }
                    if (options.isEmpty()) Text(
                        "No options yet",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    options.forEach { option ->
                        ShopListRow(
                            option.name,
                            supporting = if (option.retired) "Retired · kept for existing product values" else null,
                            icon = R.drawable.ic_category,
                            enabled = !busy && !option.retired && !field.retired,
                            actionLabel = if (option.retired) option.name else "Retire option ${option.name}",
                            onClick = if (option.retired || field.retired) null else ({
                                actions.retireOption(
                                    option.id
                                )
                            }),
                        )
                    }
                    if (!field.retired) {
                        var optionName by rememberSaveable(field.id) { mutableStateOf("") }
                        val optionError =
                            error?.takeIf { it.definitionId == field.id && it.field == ProductField.DefinitionName }
                        OutlinedTextField(
                            value = optionName,
                            onValueChange = { optionName = it; onChange() },
                            enabled = !busy,
                            singleLine = true,
                            label = { Text("New option for ${field.name}") },
                            isError = optionError != null,
                            supportingText = optionError?.let { ({ FormError(it.message) }) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ShopSecondaryButton(
                            onClick = {
                                actions.createOption(field.id, optionName) { optionName = "" }
                            },
                            enabled = !busy && LookupNames.key(optionName).isNotEmpty(),
                        ) { Text("Add option") }
                    }
                }
            }
        }
    }
}
