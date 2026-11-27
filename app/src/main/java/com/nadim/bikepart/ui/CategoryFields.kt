package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.AttributeType
import com.nadim.bikepart.shop.AttributeValue
import com.nadim.bikepart.shop.LookupNames
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.appliesTo
import com.nadim.bikepart.ui.theme.ShopSpacing

internal data class AttributeSetup(val categoryId: String, val name: String, val type: AttributeType, val required: Boolean, val unit: String)

internal class ProductLookups(
    val option: (String, String, (String) -> Unit) -> Unit,
)

internal val attributeDraftSaver = listSaver<Map<String, AttributeValue>, String>(
    save = { values -> values.flatMap { (id, value) -> when (value) {
        is AttributeValue.Text -> listOf(id, "text", value.text)
        is AttributeValue.Decimal -> listOf(id, "decimal", value.exact)
        is AttributeValue.Choices -> listOf(id, "choices", value.optionIds.sorted().joinToString(","))
    } } },
    restore = { saved -> saved.chunked(3).associate { (id, type, value) -> id to when (type) {
        "text" -> AttributeValue.Text(value)
        "decimal" -> AttributeValue.Decimal(value)
        else -> AttributeValue.Choices(value.split(',').filter { it.isNotEmpty() }.toSet())
    } } },
)

@Composable
internal fun LookupSelector(
    label: String,
    addLabel: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onCreate: ((String) -> Unit)?,
    emptyMessage: String? = null,
    useDropdown: Boolean = false,
    dropdownSingleChoice: Boolean = false,
    onDropdownRemove: (String) -> Unit = onSelect,
    dropdownSelectedOptions: List<Pair<String, String>> = options.filter { it.first in selected },
) {
    if (useDropdown) {
        SearchLookupDropdown(
            label, emptyMessage ?: stringResource(R.string.no_matching_choices), options, selected, busy, onSelect,
            onRemove = onDropdownRemove,
            singleChoice = dropdownSingleChoice,
            selectedOptions = dropdownSelectedOptions,
        )
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    ShopSearchField(query, { query = it }, label, enabled = !busy)
    val matchingOptions =
        options.filter { LookupNames.key(it.second).contains(LookupNames.key(query)) }
    val visibleOptions = options.filter {
        LookupNames.key(it.second).contains(LookupNames.key(query)) || it.first in selected
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        visibleOptions.forEach { (id, name) ->
            ShopChoiceChip(
                label = name,
                selected = id in selected,
                onClick = { onSelect(id); query = "" },
                enabled = !busy
            )
        }
    }
    if (query.isNotBlank() && matchingOptions.isEmpty() && onCreate == null && emptyMessage != null) {
        Text(emptyMessage, style = MaterialTheme.typography.bodyMedium)
    }
    if (query.isNotBlank() && onCreate != null) ShopSecondaryButton(
        onClick = { onCreate(query); query = "" }, enabled = !busy,
        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(addLabel) }
}

@Composable
internal fun CategoryFields(shop: ShopSnapshot, categoryId: String, values: Map<String, AttributeValue>, busy: Boolean,
    error: ShopError?, lookups: ProductLookups, productId: String? = null, useDropdown: Boolean, onValue: (String, AttributeValue?) -> Unit) {
    ShopSection(stringResource(R.string.category_fields)) {
        shop.attributes
            .filter { it.appliesTo(categoryId, productId) && (!it.retired || it.id in values) }
            .forEach { definition ->
            key(definition.id) {
                val value = values[definition.id]
                val label = definition.name + (if (definition.unit.isBlank()) "" else " (" + definition.unit + ")") + (if (definition.required) " (required)" else " (optional)") + if (definition.productId == "catalog-draft") " · copy only" else if (definition.productId != null) " · product only" else ""
                val fieldError = error?.takeIf { it.definitionId == definition.id }
                if (definition.retired) {
                    Text(definition.name + " (retired): " + attributeDisplay(shop, value), style = MaterialTheme.typography.bodyMedium)
                } else when (definition.type) {
                    AttributeType.Text, AttributeType.Decimal -> {
                        val text = when (value) { is AttributeValue.Text -> value.text; is AttributeValue.Decimal -> value.exact; else -> "" }
                        ShopTextField(text, { input -> onValue(definition.id, if (definition.type == AttributeType.Text) AttributeValue.Text(input) else if (input.isBlank()) null else AttributeValue.Decimal(input)) },
                            label = label, enabled = !busy, error = fieldError?.message,
                            keyboardOptions = KeyboardOptions(keyboardType = if (definition.type == AttributeType.Decimal) KeyboardType.Decimal else KeyboardType.Text),
                            singleLine = false)
                        if (definition.type == AttributeType.Decimal) Text(stringResource(R.string.up_to_18_whole_and_12_decimal), style = MaterialTheme.typography.bodySmall)
                    }
                    AttributeType.SingleChoice, AttributeType.MultipleChoice -> {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        val selected = (value as? AttributeValue.Choices)?.optionIds.orEmpty()
                        fun select(id: String) {
                            val next = if (definition.type == AttributeType.SingleChoice) (if (id in selected) emptySet() else setOf(id))
                                else if (id in selected) selected - id else selected + id
                            onValue(definition.id, AttributeValue.Choices(next))
                        }
                        val canCreateOption = definition.productId != null
                        LookupSelector(
                            if (canCreateOption && !useDropdown) "Search or add " + definition.name + " option" else "Search " + definition.name + " options",
                            "Add option",
                            shop.options.filter { it.definitionId == definition.id && (!it.retired || it.id in selected) }
                                .map { it.id to (it.name + if (it.retired) " (retired)" else "") },
                            selected,
                            busy,
                            onSelect = { select(it) },
                            onCreate = if (canCreateOption) { name ->
                                lookups.option(definition.id, name) { id ->
                                    val next = if (definition.type == AttributeType.SingleChoice) setOf(id) else selected + id
                                    onValue(definition.id, AttributeValue.Choices(next))
                                }
                            } else null,
                            emptyMessage = stringResource(R.string.lookup_no_matching_field_options, definition.name),
                            useDropdown = useDropdown,
                            dropdownSingleChoice = definition.type == AttributeType.SingleChoice,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FieldCreator(categoryId: String, busy: Boolean, error: ShopError? = null, onChange: () -> Unit = {}, create: (AttributeSetup) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AttributeType.Text) }
    var unit by rememberSaveable { mutableStateOf("") }
    var required by rememberSaveable { mutableStateOf(false) }
    ShopTextField(
        name,
        { name = it; onChange() },
        stringResource(R.string.field_name_label),
        enabled = !busy,
        error = error?.takeIf { it.field == ProductField.DefinitionName }?.message
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        AttributeType.entries.forEach { item ->
            ShopChoiceChip(
                label = item.label, selected = type == item,
                onClick = { type = item; onChange() }, enabled = !busy
            )
        }
    }
    ShopTextField(unit, { unit = it }, stringResource(R.string.field_unit_label), enabled = !busy)
    Row(Modifier
        .fillMaxWidth()
        .heightIn(min = ShopSpacing.touchTarget)
        .toggleable(
            required,
            enabled = !busy,
            role = Role.Checkbox,
            onValueChange = { required = it }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(required, onCheckedChange = null)
        Text(stringResource(R.string.field_required), modifier = Modifier.padding(ShopSpacing.small))
    }
    ShopPrimaryButton(stringResource(R.string.field_create), enabled = !busy) { create(AttributeSetup(categoryId, name, type, required, unit)) }
}

internal fun attributeDisplay(shop: ShopSnapshot, value: AttributeValue?): String = when (value) {
    is AttributeValue.Text -> value.text
    is AttributeValue.Decimal -> value.exact
    is AttributeValue.Choices -> shop.options.filter { it.id in value.optionIds }.joinToString { it.name + if (it.retired) " (retired)" else "" }
    null -> "Not provided"
}

internal val mappingDraftSaver = listSaver<Map<String, String>, String>(
    save = { it.flatMap { (source, target) -> listOf(source, target) } },
    restore = { it.chunked(2).associate { (source, target) -> source to target } },
)
