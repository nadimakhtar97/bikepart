package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

internal data class ProductFormInput(
    val catalog: CatalogDraft, val openingPieces: String, val priceRupees: String,
    val allowSimilar: Boolean, val categoryMappings: Map<String, String>, val locationIds: Set<String>,
    val photoUri: String?, val photoRemoved: Boolean,
)

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun ShopSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.primary.copy(alpha = .08f).compositeOver(colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(ShopSpacing.content), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
internal fun ShopPrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface)) { Text(label) }
}

@Composable
internal fun ShopEmptyState(title: String, description: String, icon: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = ShopSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(ShopSpacing.content).size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun HomeScreen(shop: ShopSnapshot, onAdd: () -> Unit, onStock: (StockHealth?) -> Unit, onNewSale: () -> Unit, onCarts: () -> Unit, busy: Boolean) {
    val activeProducts = shop.products.filterNot { it.archived }
    ShopSection("Your parts counter") {
        Text("${activeProducts.size} stocked products", style = MaterialTheme.typography.headlineSmall)
        Text("Work offline. Your stock stays on this phone.", style = MaterialTheme.typography.bodyMedium)
        ShopPrimaryButton("New sale", enabled = !busy, onClick = onNewSale)
        OutlinedButton(onClick = onCarts, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Unfinished carts: ${shop.carts.size}") }
        ShopPrimaryButton("Add product", onClick = onAdd)
        OutlinedButton(onClick = { onStock(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("View stock") }
    }
    if (activeProducts.isEmpty()) {
        if (shop.products.isEmpty()) ShopEmptyState("Start with your first product",
            "Record its opening stock and whole-rupee selling price.", R.drawable.ic_stock)
        else ShopEmptyState("No active products", "Add a product for current work. Archived products and their history are available under More.", R.drawable.ic_stock)
    }
    if (activeProducts.isNotEmpty()) ShopSection("Stock attention") {
        for (health in listOf(StockHealth.LowStock, StockHealth.OutOfStock)) {
            val count = activeProducts.count { it.stockHealth == health }
            OutlinedButton(onClick = { onStock(health) }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) {
                Icon(painterResource(R.drawable.ic_stock), contentDescription = null)
                Spacer(Modifier.width(ShopSpacing.small))
                Text("${health.label}: $count products")
            }
        }
    }
}

@Composable
internal fun StockScreen(shop: ShopSnapshot, onAdd: () -> Unit, onOpen: (String) -> Unit, loadPhoto: PhotoLoader, initialHealth: String? = null) {
    ShopPrimaryButton("Add product", onClick = onAdd)
    if (shop.products.none { !it.archived }) {
        if (shop.products.isEmpty()) ShopEmptyState("No products yet", "Add a product to start tracking your stock.", R.drawable.ic_stock)
        else ShopEmptyState("No active products", "Add a product for current work. Archived products and their history are available under More.", R.drawable.ic_stock)
    }
    else StockSearch(shop, initialHealth, loadPhoto, onOpen)
}
@Composable
internal fun ProductDetails(shop: ShopSnapshot, product: Product, onEdit: () -> Unit, onManage: () -> Unit, loadPhoto: PhotoLoader, busy: Boolean, onCopy: () -> Unit, onTemplate: () -> Unit, onCorrection: (CorrectionKind) -> Unit, onArchive: () -> Unit, archiveError: String?) {
    Text(product.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(shop.categories.single { it.id == product.categoryId }.name, style = MaterialTheme.typography.bodyMedium)
    if (product.archived) Text("Archived product", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    ProductPhoto(product.photo, loadPhoto, Modifier.fillMaxWidth().height(160.dp))
    ShopSection("Stock and selling price") {
        Text("${indianNumber(product.quantity)} pieces", style = MaterialTheme.typography.headlineSmall)
        if (!product.archived) StockHealthIndicator(product)
        Text("Selling price: ₹${indianNumber(product.priceRupees)}", style = MaterialTheme.typography.titleMedium)
    }
    if (!product.archived) ShopSection("Correct stock") {
        CorrectionKind.entries.forEach { kind ->
            OutlinedButton(onClick = { onCorrection(kind) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Record ${kind.label.lowercase()}") }
        }
    }
    if (shop.missingRequired(product.id).isNotEmpty()) Text("Catalog incomplete · complete required fields when editing. Stock and selling price remain available.", style = MaterialTheme.typography.bodyMedium)
    ShopSection("Product details") {
        Text(product.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyLarge)
        shop.attributes.filter { it.appliesTo(product.categoryId, product.id) && (!it.retired || it.id in product.attributes) }.forEach { definition ->
            val value = product.attributes[definition.id]
            val display = if (value == null && definition.required && !definition.retired) "Required · missing" else attributeDisplay(shop, value)
            Text(definition.name + (if (definition.retired) " (retired)" else "") + ": " + display + (if (value != null && definition.unit.isNotBlank()) " " + definition.unit else ""), style = MaterialTheme.typography.bodyMedium)
        }
        Text("SKU: ${product.sku}", style = MaterialTheme.typography.bodyMedium)
        Text("Opening stock: ${indianNumber(shop.openings[product.id]?.quantity ?: 0L)} pieces", style = MaterialTheme.typography.bodyMedium)
    }
    if (product.historicalAttributes.isNotEmpty()) ShopSection("Historical attributes") {
        product.historicalAttributes.forEach { (_, definitionId, value) ->
            val definition = shop.attributes.single { it.id == definitionId }
            Text(definition.name + ": " + attributeDisplay(shop, value) + " " + definition.unit, style = MaterialTheme.typography.bodyMedium)
        }
    }
    CorrectionHistory(shop.corrections.filter { it.productId == product.id })
    FindingSummary(shop, product)
    if (!product.archived) {
        ShopPrimaryButton("Copy catalog details", enabled = !busy, onClick = onCopy)
        OutlinedButton(onTemplate, enabled = !busy, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Create template from product") }
        ShopPrimaryButton("Edit details", enabled = !busy, onClick = onEdit)
        OutlinedButton(onClick = onManage, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Manage fields") }
        ShopSection("Archive unused product") {
            Text("Requires zero stock and no unfinished carts. Identity, details, stock history and receipts remain readable.", style = MaterialTheme.typography.bodyMedium)
            if (archiveError != null) FormError(archiveError)
            OutlinedButton(onClick = onArchive, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Archive product") }
        }
    }
}

@Composable
internal fun ArchivedProducts(shop: ShopSnapshot, loadPhoto: PhotoLoader, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(query) { mutableIntStateOf(40) }
    val archived = shop.products.filter { it.archived }
    if (archived.isEmpty()) ShopEmptyState("No archived products", "Products archived at zero stock remain here with their details and history.", R.drawable.ic_stock)
    else {
        OutlinedTextField(query, { query = it }, label = { Text("Search archived products") }, modifier = Modifier.fillMaxWidth())
        val found = archived.filter { LookupNames.key(it.title + " " + it.sku).contains(LookupNames.key(query)) }
        Text("${found.size} archived products", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (found.isEmpty()) ShopEmptyState("No matching archived products", "Try a title or permanent SKU.", R.drawable.ic_stock)
        found.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto) { onOpen(product.id) } }
        if (found.size > limit) OutlinedButton(onClick = { limit += 40 }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Show more archived products") }
    }
}

@Composable
internal fun AppearanceSettings(appearance: Appearance, busy: Boolean, onAppearance: (Appearance) -> Unit, onCategories: () -> Unit, onFindingLookups: () -> Unit) {
    ShopSection("Appearance") {
        Text("Now in Android · Default theme", style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                    .selectable(appearance == mode, enabled = !busy, role = Role.RadioButton, onClick = { onAppearance(mode) })
                    .padding(horizontal = ShopSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appearance == mode, onClick = null)
                    Text(mode.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(ShopSpacing.content))
                }
            }
        }
        Text("System follows your phone’s light or dark appearance.", style = MaterialTheme.typography.bodyMedium)
    }
    ShopSection("Catalog setup") {
        OutlinedButton(onClick = onFindingLookups, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Manage fitment and locations") }
        OutlinedButton(onClick = onCategories, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Edit categories") }
    }
}

@Composable
internal fun CategorySettings(shop: ShopSnapshot, busy: Boolean, onRename: (String, String) -> Unit) {
    Text("Rename your starter categories to suit the shop.", style = MaterialTheme.typography.bodyMedium)
    shop.categories.forEach { category ->
        key(category.id) {
            var name by rememberSaveable(category.name) { mutableStateOf(category.name) }
            ShopSection(category.name) {
                OutlinedTextField(name, { name = it }, enabled = !busy, label = { Text("Category name") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(enabled = !busy && name != category.name, onClick = { onRename(category.id, name) },
                    modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Save ${category.name}") }
            }
        }
    }
}

@Composable
internal fun ProductForm(shop: ShopSnapshot, product: Product?, busy: Boolean, error: ShopError?, onChange: () -> Unit, lookups: ProductLookups, findingLookups: FindingLookups, loadPhoto: PhotoLoader, initialCatalog: CatalogDraft? = null, templateMode: Boolean = false, save: (ProductFormInput) -> Unit) {
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
    var price by rememberSaveable { mutableStateOf("") }
    var distinct by rememberSaveable { mutableStateOf(false) }
    var mappings by rememberSaveable(stateSaver = mappingDraftSaver) { mutableStateOf(emptyMap<String, String>()) }
    var values by rememberSaveable(stateSaver = attributeDraftSaver) { mutableStateOf(product?.attributes ?: initialCatalog?.attributes ?: emptyMap()) }
    var customPayload by rememberSaveable { mutableStateOf(CatalogDraft(customFields = initialCatalog?.customFields.orEmpty()).encode()) }
    val customFields = decodeCatalog(customPayload).customFields
    fun updateCustom(fields: List<DraftField>) { customPayload = CatalogDraft(customFields = fields).encode(); onChange() }
    val formShop = shop.copy(attributes = shop.attributes + customFields.map { CategoryAttribute(it.id, category, it.name, it.type, it.required, it.unit, "catalog-draft") }, options = shop.options + customFields.flatMap { it.options })
    val formLookups = ProductLookups(lookups.category, lookups.attribute) { definition, name, selected ->
        val field = customFields.find { it.id == definition }
        if (field == null) lookups.option(definition, name, selected)
        else if (LookupNames.key(name).isNotEmpty()) {
            val option = field.options.find { LookupNames.key(it.name) == LookupNames.key(name) }
                ?: AttributeOption(java.util.UUID.randomUUID().toString(), definition, LookupNames.display(name))
            updateCustom(customFields.map { if (it.id == definition) it.copy(options = (it.options + option).distinctBy { item -> item.id }) else it })
            selected(option.id)
        }
    }
    if (templateMode || initialCatalog != null) Text("Catalog details only. Enter stock, selling price and locations separately for each new product.", style = MaterialTheme.typography.bodyMedium)
    ShopSection("Basic details") {
        ProductTextField(title, { title = it; distinct = false; onChange() }, "Title (required)", !busy, error?.takeIf { it.field == ProductField.Title })
        Text("Category (required)", style = MaterialTheme.typography.labelLarge)
        LookupSelector("Search or add category", "Add category", shop.categories.map { it.id to it.name }, setOf(category), busy,
            onSelect = { category = it; mappings = emptyMap(); distinct = false; onChange() },
            onCreate = { name -> lookups.category(name) { category = it; mappings = emptyMap(); distinct = false; onChange() } })
        if (error?.field == ProductField.Category) FormError(error.message)
        ProductTextField(description, { description = it; onChange() }, "Description", !busy)
    }
    PhotoField(product?.photo ?: initialCatalog?.photo, photoUri, photoRemoved, busy, loadPhoto, error?.takeIf { it.field == ProductField.Photo }?.message,
        select = { photoUri = it; photoRemoved = false; onChange() },
        remove = { photoUri = null; photoRemoved = true; onChange() })
    if (templateMode && initialCatalog != null && initialCatalog.categoryId.isNotEmpty() && category != initialCatalog.categoryId) Text("Previous category values will be retained as historical attributes.", style = MaterialTheme.typography.bodyMedium)
    if (product != null && category != product.categoryId) CategoryMapping(shop, product, category, mappings, busy) { mappings = it }
    if (category.isNotEmpty()) CategoryFields(formShop, category, values, busy, error, formLookups, if (product != null) product.id else "catalog-draft",
        onValue = { id, value -> values = values.toMutableMap().apply { if (value == null) remove(id) else put(id, value) }; onChange() })
    if (product == null) DraftCustomFields(customFields, category, busy, error, ::updateCustom)
    FindingFields(shop, product, models, locations, busy, findingError ?: error, findingLookups, assigned,
        onAssigned = { assigned = it; findingError = null; onChange() },
        onModels = { models = it; findingError = null; onChange() }, onLocations = { locations = it; onChange() }, showLocations = !templateMode)
    if (product == null && !templateMode) ShopSection("Opening stock and price") {
        ProductTextField(quantity, { quantity = it; onChange() }, "Opening stock (pieces)", !busy,
            error?.takeIf { it.field == ProductField.OpeningStock }, number = true)
        ProductTextField(price, { price = it; onChange() }, "Selling price (whole rupees)", !busy,
            error?.takeIf { it.field == ProductField.SellingPrice }, number = true)
        Text("Stock is counted in whole pieces. Prices use whole rupees.", style = MaterialTheme.typography.bodyMedium)
    }
    if (error != null && error.field == null) FormError(error.message)
    if (error?.field == ProductField.DefinitionName && error.definitionId == null) FormError(error.message)
    if (error?.similar == true) Row(
        Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).toggleable(distinct, enabled = !busy, role = Role.Checkbox, onValueChange = { distinct = it }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = distinct, onCheckedChange = null)
        Text("Save as a distinct product", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(ShopSpacing.medium))
    }
    ShopPrimaryButton(if (busy) "Saving…" else if (templateMode) "Save template" else "Save product", enabled = !busy) {
        if (assigned && models.isEmpty()) {
            findingError = ShopError("Choose at least one motorcycle model", ProductField.Fitment)
            return@ShopPrimaryButton
        }
        val applicable = values.filterKeys { id -> formShop.attributes.any { it.id == id && it.appliesTo(category, product?.id ?: "catalog-draft") } }
        save(ProductFormInput(CatalogDraft(title, category, description, applicable, customFields, models, initialCatalog?.photo),
            quantity, price, distinct, mappings, locations, photoUri, photoRemoved))
    }
}

@Composable
private fun ProductTextField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, error: ShopError? = null, number: Boolean = false) {
    OutlinedTextField(value, onChange, enabled = enabled, label = { Text(label) }, isError = error != null,
        supportingText = if (error == null) null else ({ FormError(error.message) }),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun FormError(message: String) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { requester.bringIntoView() }
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.bringIntoViewRequester(requester).semantics { liveRegion = LiveRegionMode.Assertive; error(message) })
}

@Composable
private fun CategoryMapping(shop: ShopSnapshot, product: Product, categoryId: String, mappings: Map<String, String>, busy: Boolean, onChange: (Map<String, String>) -> Unit) {
    ShopSection("Category change") {
        Text("Previous category values will be kept as historical attributes. Optionally copy compatible text or decimal values below.", style = MaterialTheme.typography.bodyMedium)
        shop.attributes.filter { it.productId == null && it.categoryId == product.categoryId && it.id in product.attributes }.forEach { source ->
            val targets = shop.attributes.filter { it.productId == null && it.categoryId == categoryId && !it.retired && it.type == source.type && it.unit == source.unit && !it.type.isChoice }
            if (targets.isNotEmpty()) {
                Text("Copy " + source.name, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                    targets.forEach { target ->
                        FilterChip(selected = mappings[source.id] == target.id, enabled = !busy,
                            onClick = { onChange(if (mappings[source.id] == target.id) mappings - source.id else mappings + (source.id to target.id)) },
                            label = { Text(target.name) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
                    }
                }
            }
        }
    }
}
