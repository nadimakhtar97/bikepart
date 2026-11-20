package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.LookupNames
import com.nadim.bikepart.shop.ProductTemplate
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun TemplateList(shop: ShopSnapshot, busy: Boolean, onCreate: () -> Unit, onOpen: (String) -> Unit, onUse: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var retired by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.template_note), style = MaterialTheme.typography.bodyMedium)
    ShopPrimaryButton(stringResource(R.string.template_create), enabled = !busy, onClick = onCreate)
    ShopSearchField(search, { search = it }, "Search templates", enabled = !busy)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
        ShopChoiceChip(
            label = stringResource(R.string.template_active),
            selected = !retired,
            onClick = { retired = false },
            enabled = !busy
        )
        ShopChoiceChip(
            label = stringResource(R.string.template_retired),
            selected = retired,
            onClick = { retired = true },
            enabled = !busy
        )
    }
    val matches = shop.templates.filter { it.retired == retired && LookupNames.key(it.catalog.title + " " + it.catalog.description).contains(LookupNames.key(search)) }
    if (matches.isEmpty()) ShopEmptyState(stringResource(R.string.template_no_matching_title), "Create a template or try another search.", com.nadim.bikepart.R.drawable.ic_template)
    matches.forEach { template ->
        ShopSection(template.catalog.title) {
            Text(shop.categories.single { it.id == template.catalog.categoryId }.name, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small), verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                ShopSecondaryButton({ onOpen(template.id) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Open template: ${template.catalog.title}" }) { Text(stringResource(R.string.template_open)) }
                if (!retired) TextButton({ onUse(template.id) }, enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = ShopSpacing.touchTarget)
                        .semantics {
                            contentDescription = "Use template: ${template.catalog.title}"
                        }) { Text(stringResource(R.string.template_use_primary)) }
            }
        }
    }
}

@Composable
internal fun TemplateDetails(shop: ShopSnapshot, template: ProductTemplate, busy: Boolean, loadPhoto: PhotoLoader, onEdit: () -> Unit, onUse: () -> Unit, onRetire: () -> Unit) {
    val draft = template.catalog
    ShopPageHeader(draft.title, if (template.retired) "Retired template · retained for reference" else "Product template")
    if (draft.photo != null) ProductPhoto(draft.photo, loadPhoto,
        Modifier
            .fillMaxWidth()
            .height(ShopSpacing.touchTarget * 3))
    ShopSection(stringResource(R.string.template_catalog_details)) {
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
    if (draft.historicalAttributes.isNotEmpty()) ShopSection(stringResource(R.string.product_historical_attributes)) {
        draft.historicalAttributes.forEach { item ->
            val definition = shop.attributes.single { it.id == item.definitionId }
            Text(definition.name + ": " + attributeDisplay(shop, item.value) + " " + definition.unit, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (!template.retired) {
        ShopPrimaryButton(stringResource(R.string.template_use_primary), enabled = !busy, onClick = onUse)
        ShopSecondaryButton(onEdit, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.template_edit)) }
        ShopSecondaryButton(onRetire, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.template_retire)) }
    }
}
