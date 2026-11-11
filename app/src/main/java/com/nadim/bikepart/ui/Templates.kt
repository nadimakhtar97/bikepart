package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun TemplateList(shop: ShopSnapshot, busy: Boolean, onCreate: () -> Unit, onOpen: (String) -> Unit, onUse: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var retired by rememberSaveable { mutableStateOf(false) }
    Text("Reusable catalog details. Templates have no SKU or stock.", style = MaterialTheme.typography.bodyMedium)
    ShopPrimaryButton("Create template", enabled = !busy, onClick = onCreate)
    OutlinedTextField(search, { search = it }, enabled = !busy, label = { Text("Search templates") }, modifier = Modifier.fillMaxWidth())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        FilterChip(!retired, { retired = false }, enabled = !busy, label = { Text("Active templates") }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
        FilterChip(retired, { retired = true }, enabled = !busy, label = { Text("Retired templates") }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
    }
    val matches = shop.templates.filter { it.retired == retired && LookupNames.key(it.catalog.title + " " + it.catalog.description).contains(LookupNames.key(search)) }
    if (matches.isEmpty()) Text("No matching templates", style = MaterialTheme.typography.bodyMedium)
    matches.forEach { template ->
        ShopSection(template.catalog.title) {
            Text(shop.categories.single { it.id == template.catalog.categoryId }.name, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton({ onOpen(template.id) }, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget).semantics { contentDescription = "Open template: ${template.catalog.title}" }) { Text("Open template") }
            if (!retired) ShopPrimaryButton("Use template", enabled = !busy, modifier = Modifier.semantics { contentDescription = "Use template: ${template.catalog.title}" }) { onUse(template.id) }
        }
    }
}

@Composable
internal fun TemplateDetails(shop: ShopSnapshot, template: ProductTemplate, busy: Boolean, loadPhoto: PhotoLoader, onEdit: () -> Unit, onUse: () -> Unit, onRetire: () -> Unit) {
    val draft = template.catalog
    Text(draft.title, style = MaterialTheme.typography.headlineSmall)
    Text(if (template.retired) "Retired template · retained for reference" else "Product template", style = MaterialTheme.typography.bodyMedium)
    ProductPhoto(draft.photo, loadPhoto, Modifier.fillMaxWidth().height(ShopSpacing.touchTarget * 3))
    ShopSection("Catalog details") {
        Text(shop.categories.single { it.id == draft.categoryId }.name, style = MaterialTheme.typography.titleMedium)
        Text(draft.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyMedium)
        val options = shop.copy(options = shop.options + draft.customFields.flatMap { it.options })
        draft.attributes.forEach { (id, value) ->
            val name = draft.customFields.find { it.id == id }?.name ?: shop.attributes.single { it.id == id }.name
            Text(name + ": " + attributeDisplay(options, value), style = MaterialTheme.typography.bodyMedium)
        }
        Text(if (draft.modelIds.isEmpty()) "Fitment: Unknown" else "Fitment: Assigned", style = MaterialTheme.typography.bodyMedium)
        draft.modelIds.forEach { id ->
            val model = shop.models.single { it.id == id }
            Text(shop.makes.single { it.id == model.makeId }.name + " · " + model.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (draft.historicalAttributes.isNotEmpty()) ShopSection("Historical attributes") {
        draft.historicalAttributes.forEach { item ->
            val definition = shop.attributes.single { it.id == item.definitionId }
            Text(definition.name + ": " + attributeDisplay(shop, item.value) + " " + definition.unit, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (!template.retired) {
        ShopPrimaryButton("Use template", enabled = !busy, onClick = onUse)
        OutlinedButton(onEdit, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Edit template") }
        OutlinedButton(onRetire, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Retire template") }
    }
}

@Composable
internal fun DraftCustomFields(fields: List<DraftField>, category: String, busy: Boolean, error: ShopError?, update: (List<DraftField>) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    ShopSection("Custom fields for this copy") {
        Text("These definitions and their choices belong to this draft. Saved copies are independent.", style = MaterialTheme.typography.bodyMedium)
        fields.forEach { field -> key(field.id) {
            val fieldError = error?.takeIf { it.field == ProductField.DefinitionName && it.definitionId == field.id }
            OutlinedTextField(field.name, { name -> update(fields.map { if (it.id == field.id) it.copy(name = name) else it }) }, enabled = !busy,
                label = { Text("Custom field name") }, isError = fieldError != null,
                supportingText = fieldError?.let { ({ FormError(it.message) }) }, modifier = Modifier.fillMaxWidth())
            OutlinedButton({ update(fields.filter { it.id != field.id }) }, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Remove custom field: ${field.name}") }
        } }
        OutlinedButton({ creating = !creating }, enabled = !busy && category.isNotEmpty(), modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Add custom field to draft") }
        if (creating) FieldCreator(category, busy, error) { input ->
            update(fields + DraftField(java.util.UUID.randomUUID().toString(), input.name, input.type, input.required, input.unit))
            creating = false
        }
    }
}
