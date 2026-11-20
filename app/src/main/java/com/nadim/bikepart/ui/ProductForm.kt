package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.CatalogDraft
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.shop.appliesTo
import com.nadim.bikepart.shop.decodeCatalog
import com.nadim.bikepart.shop.encode
import com.nadim.bikepart.ui.theme.ShopSpacing

internal data class ProductFormInput(
    val catalog: CatalogDraft, val openingPieces: String, val priceRupees: String,
    val allowSimilar: Boolean, val categoryMappings: Map<String, String>, val locationIds: Set<String>,
    val photoUri: String?, val photoRemoved: Boolean,
)

@Composable
internal fun ProductForm(
    shop: ShopSnapshot,
    product: Product?,
    busy: Boolean,
    error: ShopError?,
    onChange: () -> Unit,
    lookups: ProductLookups,
    findingLookups: FindingLookups,
    loadPhoto: PhotoLoader,
    onManageCategory: (String) -> Unit,
    initialCatalog: CatalogDraft? = null,
    templateMode: Boolean = false,
    save: (ProductFormInput) -> Unit
) {
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoRemoved by rememberSaveable { mutableStateOf(false) }
    var models by rememberSaveable(stateSaver = idSetSaver) { mutableStateOf(product?.modelIds ?: initialCatalog?.modelIds ?: emptySet()) }
    var locations by rememberSaveable(stateSaver = idSetSaver) { mutableStateOf(product?.locationIds ?: emptySet()) }
    var assigned by rememberSaveable { mutableStateOf(models.isNotEmpty()) }
    var findingError by remember { mutableStateOf<ShopError?>(null) }
    var title by rememberSaveable { mutableStateOf(product?.title ?: initialCatalog?.title ?: "") }
    var category by rememberSaveable { mutableStateOf(product?.categoryId ?: initialCatalog?.categoryId ?: "") }
    var description by rememberSaveable { mutableStateOf(product?.description ?: initialCatalog?.description ?: "") }
    var quantity by rememberSaveable { mutableStateOf(if (initialCatalog != null && !templateMode) "" else "0") }
    var price by rememberSaveable { mutableStateOf(product?.priceRupees?.toString() ?: "") }
    var distinct by rememberSaveable { mutableStateOf(false) }
    var mappings by rememberSaveable(stateSaver = mappingDraftSaver) { mutableStateOf(emptyMap<String, String>()) }
    var values by rememberSaveable(stateSaver = attributeDraftSaver) { mutableStateOf(product?.attributes ?: initialCatalog?.attributes ?: emptyMap()) }
    var customPayload by rememberSaveable { mutableStateOf(CatalogDraft(customFields = initialCatalog?.customFields.orEmpty()).encode()) }
    val customFields = decodeCatalog(customPayload).customFields

    ShopSection(stringResource(R.string.form_basic_details)) {
        if (templateMode || initialCatalog != null) Text(
            stringResource(R.string.form_catalog_only_note),
            style = MaterialTheme.typography.bodyMedium
        )
        PhotoField(
            product?.photo ?: initialCatalog?.photo,
            photoUri,
            photoRemoved,
            busy,
            loadPhoto,
            error?.takeIf { it.field == ProductField.Photo }?.message,
            select = { photoUri = it; photoRemoved = false; onChange() },
            remove = { photoUri = null; photoRemoved = true; onChange() })
        ProductTextField(title, { title = it; distinct = false; onChange() }, "Title (required)", !busy, error?.takeIf { it.field == ProductField.Title })
        Text(stringResource(R.string.form_category_required), style = MaterialTheme.typography.labelLarge)
        LookupSelector("Search or add category", "Add category", shop.categories.map { it.id to it.name }, setOf(category), busy,
            onSelect = { category = it; mappings = emptyMap(); distinct = false; onChange() },
            onCreate = { name -> lookups.category(name) { category = it; mappings = emptyMap(); distinct = false; onChange() } })
        if (error?.field == ProductField.Category) FormError(error.message)
        ProductTextField(description, { description = it; onChange() }, "Description", !busy)
    }
    if (templateMode && initialCatalog != null && initialCatalog.categoryId.isNotEmpty() && category != initialCatalog.categoryId) Text(stringResource(R.string.form_prev_category_note), style = MaterialTheme.typography.bodyMedium)
    if (category.isNotEmpty()) {
        val categoryName = shop.categories.firstOrNull { it.id == category }?.name ?: "category"
        ShopSecondaryButton(
            onClick = { onManageCategory(category) },
            enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
        ) { Text("Manage $categoryName fields in More") }
        CategoryFields(
            shop,
            category,
            values,
            busy,
            error,
            lookups,
            if (product != null) product.id else "catalog-draft",
            onValue = { id, value ->
                values = values.toMutableMap()
                    .apply { if (value == null) remove(id) else put(id, value) }; onChange()
            })
    }
    if (product != null && category != product.categoryId) CategoryMapping(
        shop,
        product,
        category,
        mappings,
        busy
    ) { mappings = it }
    FindingFields(shop, product, models, locations, busy, findingError ?: error, findingLookups, assigned,
        onAssigned = { assigned = it; findingError = null; onChange() },
        onModels = { models = it; findingError = null; onChange() }, onLocations = { locations = it; onChange() }, showLocations = !templateMode)
    if (product != null && !product.archived && !templateMode) ShopSection(stringResource(R.string.form_selling_price)) {
        ProductTextField(
            price, { price = it; onChange() }, "Selling price (whole rupees)", !busy,
            error?.takeIf { it.field == ProductField.SellingPrice }, number = true
        )
        Text(
            stringResource(R.string.form_price_update_note),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (product == null && !templateMode) ShopSection(stringResource(R.string.form_opening_stock_price)) {
        val stockField: @Composable (Modifier) -> Unit = { fieldModifier ->
            ProductTextField(quantity, { quantity = it; onChange() }, "Opening stock (pieces)", !busy,
                error?.takeIf { it.field == ProductField.OpeningStock }, number = true, modifier = fieldModifier)
        }
        val priceField: @Composable (Modifier) -> Unit = { fieldModifier ->
            ProductTextField(price, { price = it; onChange() }, "Selling price (whole rupees)", !busy,
                error?.takeIf { it.field == ProductField.SellingPrice }, number = true, modifier = fieldModifier)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Roomier screens keep the two short numeric fields on one line; phones stay stacked
            // so 200% font scale never clips their labels.
            if (maxWidth >= 480.dp && LocalDensity.current.fontScale < 1.5f) Row(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.medium)) {
                Box(Modifier.weight(1f)) { stockField(Modifier.fillMaxWidth()) }
                Box(Modifier.weight(1f)) { priceField(Modifier.fillMaxWidth()) }
            } else Column(verticalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                stockField(Modifier.fillMaxWidth())
                priceField(Modifier.fillMaxWidth())
            }
        }
        Text(stringResource(R.string.form_stock_price_note), style = MaterialTheme.typography.bodyMedium)
    }
    if (error != null && error.field == null) FormError(error.message)
    if (error?.field == ProductField.DefinitionName && error.definitionId == null) FormError(error.message)
    if (error?.similar == true) Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ShopSpacing.touchTarget)
            .toggleable(
                distinct,
                enabled = !busy,
                role = Role.Checkbox,
                onValueChange = { distinct = it }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = distinct, onCheckedChange = null)
        Text(stringResource(R.string.form_save_distinct), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(ShopSpacing.medium))
    }
    ShopPrimaryButton(if (busy) "Saving…" else if (templateMode) "Save template" else "Save product", enabled = !busy) {
        if (assigned && models.isEmpty()) {
            findingError = ShopError("Choose at least one motorcycle model", ProductField.Fitment)
            return@ShopPrimaryButton
        }
        val applicable = values.filterKeys { id ->
            customFields.any { it.id == id } ||
                    shop.attributes.any {
                        it.id == id && it.appliesTo(
                            category,
                            product?.id ?: "catalog-draft"
                        )
                    }
        }
        save(ProductFormInput(CatalogDraft(title, category, description, applicable, customFields, models, initialCatalog?.photo),
            quantity, price, distinct, mappings, locations, photoUri, photoRemoved))
    }
}

@Composable
private fun ProductTextField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, error: ShopError? = null, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (number) ShopTextField(
        value, onChange, label, enabled = enabled, error = error?.message,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier
    )
    else OutlinedTextField(
        value,
        onChange,
        enabled = enabled,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error == null) null else ({ FormError(error.message) }),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier)
}

@Composable
private fun CategoryMapping(shop: ShopSnapshot, product: Product, categoryId: String, mappings: Map<String, String>, busy: Boolean, onChange: (Map<String, String>) -> Unit) {
    ShopSection(stringResource(R.string.category_change)) {
        Text(stringResource(R.string.previous_category_values_will_be_kept_as), style = MaterialTheme.typography.bodyMedium)
        shop.attributes.filter { it.productId == null && it.categoryId == product.categoryId && it.id in product.attributes }.forEach { source ->
            val targets = shop.attributes.filter { it.productId == null && it.categoryId == categoryId && !it.retired && it.type == source.type && it.unit == source.unit && !it.type.isChoice }
            if (targets.isNotEmpty()) {
                Text(stringResource(R.string.copy) + source.name, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                    targets.forEach { target ->
                        ShopChoiceChip(
                            label = target.name,
                            selected = mappings[source.id] == target.id,
                            enabled = !busy,
                            onClick = { onChange(if (mappings[source.id] == target.id) mappings - source.id else mappings + (source.id to target.id)) })
                    }
                }
            }
        }
    }
}
