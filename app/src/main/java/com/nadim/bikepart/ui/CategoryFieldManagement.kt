package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.pluralStringResource
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
            stringResource(R.string.category_unavailable),
            stringResource(R.string.return_to_categories_and_choose_an),
            R.drawable.ic_category
        )
        return
    }

    val fields = shop.attributes.filter { it.categoryId == categoryId && it.productId == null }
    ShopPageHeader(category.name, stringResource(R.string.shared_fields_and_choice_options_for))
    ShopSectionHeader(
        stringResource(R.string.category_fields),
        supporting = if (fields.isEmpty()) stringResource(R.string.no_shared_fields_yet) else pluralStringResource(R.plurals.shared_category_field_count, fields.size, fields.size),
    )

    var creating by rememberSaveable(categoryId) { mutableStateOf(false) }
    ShopSecondaryButton(
        onClick = { creating = !creating },
        enabled = !busy,
        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
    ) { Text(if (creating) stringResource(R.string.cancel) else stringResource(R.string.catalog_add_category_field)) }
    if (creating) {
        ShopSection(stringResource(R.string.new_category_field)) {
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
            stringResource(R.string.no_category_fields),
            stringResource(R.string.add_a_field_to_describe_products, category.name),
            R.drawable.ic_category
        )
    }

    fields.forEach { field ->
        key(field.id) {
            ShopSection(field.name) {
                Text(
                    (if (field.required) stringResource(R.string.required) else stringResource(R.string.optional)) + " · " + stringResource(field.type.label) +
                            if (field.unit.isBlank()) "" else stringResource(R.string.middle_dot_separator, field.unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (field.retired) {
                    Text(
                        stringResource(R.string.retired_retained_for_existing_product_details),
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
                        ) { Text(if (field.required) stringResource(R.string.field_make_optional) else stringResource(R.string.field_make_required)) }
                        ShopSecondaryButton(
                            onClick = { actions.retire(field.id) },
                            enabled = !busy,
                        ) { Text(stringResource(R.string.catalog_retire_field)) }
                        ShopSecondaryButton(
                            onClick = { replacing = !replacing; onChange() },
                            enabled = !busy,
                        ) { Text(if (replacing) stringResource(R.string.cancel_replacement) else stringResource(R.string.catalog_replace_field_type)) }
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
                        stringResource(R.string.options),
                        supporting = stringResource(R.string.options_belong_to, field.name),
                    )
                    val options = shop.options.filter { it.definitionId == field.id }
                    if (options.isEmpty()) Text(
                        stringResource(R.string.no_options_yet),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    options.forEach { option ->
                        ShopListRow(
                            option.name,
                            supporting = if (option.retired) stringResource(R.string.retired_kept_for_existing_product_values) else null,
                            icon = R.drawable.ic_category,
                            enabled = !busy && !option.retired && !field.retired,
                            actionLabel = if (option.retired) option.name else stringResource(R.string.retire_option_2, option.name),
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
                        ShopTextField(
                            value = optionName,
                            onValueChange = { optionName = it; onChange() },
                            label = stringResource(R.string.new_option_for, field.name),
                            enabled = !busy,
                            error = optionError?.message,
                        )
                        ShopSecondaryButton(
                            onClick = {
                                actions.createOption(field.id, optionName) { optionName = "" }
                            },
                            enabled = !busy && LookupNames.key(optionName).isNotEmpty(),
                        ) { Text(stringResource(R.string.add_option)) }
                    }
                }
            }
        }
    }
}
