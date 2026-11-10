package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

internal data class AttributeSetup(val categoryId: String, val name: String, val type: AttributeType, val required: Boolean, val unit: String)

internal class ProductLookups(
    val category: (String, (String) -> Unit) -> Unit,
    val attribute: (AttributeSetup, () -> Unit) -> Unit,
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
internal fun LookupSelector(label: String, addLabel: String, options: List<Pair<String, String>>, selected: Set<String>, busy: Boolean,
    onSelect: (String) -> Unit, onCreate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, enabled = !busy, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        options.filter { LookupNames.key(it.second).contains(LookupNames.key(query)) || it.first in selected }.forEach { (id, name) ->
            FilterChip(selected = id in selected, onClick = { onSelect(id); query = "" }, enabled = !busy,
                label = { Text(name) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
        }
    }
    if (query.isNotBlank()) OutlinedButton(onClick = { onCreate(query); query = "" }, enabled = !busy,
        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(addLabel) }
}

@Composable
internal fun CategoryFields(shop: ShopSnapshot, categoryId: String, values: Map<String, AttributeValue>, busy: Boolean,
    error: ShopError?, lookups: ProductLookups, productId: String? = null, onValue: (String, AttributeValue?) -> Unit) {
    ShopSection("Category fields") {
        var search by rememberSaveable(categoryId) { mutableStateOf("") }
        var creating by rememberSaveable(categoryId) { mutableStateOf(false) }
        OutlinedTextField(search, { search = it }, enabled = !busy, label = { Text("Search fields") }, modifier = Modifier.fillMaxWidth())
        shop.attributes.filter { it.appliesTo(categoryId, productId) && (!it.retired || it.id in values) && (LookupNames.key(it.name).contains(LookupNames.key(search)) || error?.definitionId == it.id) }.forEach { definition ->
            key(definition.id) {
                val value = values[definition.id]
                val label = definition.name + (if (definition.unit.isBlank()) "" else " (" + definition.unit + ")") + (if (definition.required) " (required)" else " (optional)") + if (definition.productId != null) " · product only" else ""
                val fieldError = error?.takeIf { it.definitionId == definition.id }
                if (definition.retired) {
                    Text(definition.name + " (retired): " + attributeDisplay(shop, value), style = MaterialTheme.typography.bodyMedium)
                } else when (definition.type) {
                    AttributeType.Text, AttributeType.Decimal -> {
                        val text = when (value) { is AttributeValue.Text -> value.text; is AttributeValue.Decimal -> value.exact; else -> "" }
                        OutlinedTextField(text, { input -> onValue(definition.id, if (definition.type == AttributeType.Text) AttributeValue.Text(input) else if (input.isBlank()) null else AttributeValue.Decimal(input)) },
                            enabled = !busy, label = { Text(label) }, isError = fieldError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = if (definition.type == AttributeType.Decimal) KeyboardType.Decimal else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth())
                        if (definition.type == AttributeType.Decimal) Text("Up to 18 whole and 12 decimal digits. No exponent.", style = MaterialTheme.typography.bodySmall)
                    }
                    AttributeType.SingleChoice, AttributeType.MultipleChoice -> {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        val selected = (value as? AttributeValue.Choices)?.optionIds.orEmpty()
                        fun select(id: String) {
                            val next = if (definition.type == AttributeType.SingleChoice) (if (id in selected) emptySet() else setOf(id))
                                else if (id in selected) selected - id else selected + id
                            onValue(definition.id, AttributeValue.Choices(next))
                        }
                        LookupSelector("Search or add " + definition.name + " option", "Add option", shop.options.filter { it.definitionId == definition.id && (!it.retired || it.id in selected) }.map { it.id to (it.name + if (it.retired) " (retired)" else "") }, selected, busy,
                            onSelect = { select(it) }, onCreate = { name -> lookups.option(definition.id, name) { id ->
                                onValue(definition.id, AttributeValue.Choices(if (definition.type == AttributeType.SingleChoice) setOf(id) else selected + id))
                            } })
                    }
                }
                if (fieldError != null) FormError(fieldError.message)
            }
        }
        OutlinedButton(onClick = { creating = !creating }, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Add category field") }
        if (creating) FieldCreator(categoryId, busy) { setup ->
            lookups.attribute(setup) { creating = false; search = "" }
        }
    }
}

@Composable
internal fun FieldCreator(categoryId: String, busy: Boolean, create: (AttributeSetup) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(AttributeType.Text) }
    var unit by rememberSaveable { mutableStateOf("") }
    var required by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Field name") }, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        AttributeType.entries.forEach { item -> FilterChip(selected = type == item, onClick = { type = item }, enabled = !busy,
            label = { Text(item.label) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) }
    }
    OutlinedTextField(unit, { unit = it }, enabled = !busy, label = { Text("Unit label") }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).toggleable(required, enabled = !busy, role = Role.Checkbox, onValueChange = { required = it }), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(required, onCheckedChange = null)
        Text("Required field", modifier = Modifier.padding(ShopSpacing.small))
    }
    ShopPrimaryButton("Create field", enabled = !busy) { create(AttributeSetup(categoryId, name, type, required, unit)) }
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
