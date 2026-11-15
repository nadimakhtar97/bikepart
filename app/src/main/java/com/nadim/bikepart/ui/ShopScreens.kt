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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
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
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp))
    }
}

@Composable
internal fun HomeScreen(shop: ShopSnapshot, onAdd: () -> Unit, onStock: (StockHealth?) -> Unit, onNewSale: () -> Unit,
    onCarts: () -> Unit, onReceive: () -> Unit, onSales: () -> Unit, onSale: (String) -> Unit, busy: Boolean) {
    val activeProducts = shop.products.filterNot { it.archived }
    val lowStock = activeProducts.count { it.stockHealth == StockHealth.LowStock }
    val outOfStock = activeProducts.count { it.stockHealth == StockHealth.OutOfStock }
    val recentSales = shop.sales.sortedByDescending { it.recordedAt }.take(3)
    ShopPageHeader(stringResource(R.string.home_header), "Work offline. Your stock stays on this phone.")
    ShopSectionHeader(stringResource(R.string.home_quick_actions))
    ShopTileGrid { tile ->
        ShopActionTile(stringResource(R.string.home_new_sale), R.drawable.ic_cart, tile, enabled = !busy, primary = true, onClick = onNewSale)
        ShopActionTile(stringResource(R.string.home_add_product), R.drawable.ic_add, tile, enabled = !busy, onClick = onAdd)
        ShopActionTile(stringResource(R.string.product_receive_stock), R.drawable.ic_inventory, tile, enabled = !busy, onClick = onReceive)
        ShopActionTile(stringResource(R.string.more_sales_history), R.drawable.ic_receipt, tile, enabled = !busy, onClick = onSales)
    }
    ShopSectionHeader(stringResource(R.string.home_shop_overview))
    ShopTileGrid { tile ->
        ShopStatCard(stringResource(R.string.cart_list_header), shop.carts.size.toString(), R.drawable.ic_pending,
            modifier = tile, enabled = !busy, onClick = onCarts)
        ShopStatCard(stringResource(R.string.home_active_products), activeProducts.size.toString(), R.drawable.ic_inventory,
            modifier = tile, enabled = !busy, onClick = { onStock(null) })
    }
    if (activeProducts.isEmpty()) {
        if (shop.products.isEmpty()) ShopEmptyState(stringResource(R.string.home_empty_title),
            "Record its opening stock and whole-rupee selling price.", R.drawable.ic_stock)
        else ShopEmptyState(stringResource(R.string.home_no_active_title), "Add a product for current work. Archived products and their history are available under More.", R.drawable.ic_stock)
    }
    if (activeProducts.isNotEmpty()) {
        ShopSectionHeader(stringResource(R.string.home_stock_attention),
            supporting = if (lowStock + outOfStock == 0) "Nothing needs attention today" else "Counts at or below your thresholds")
        ShopListGroup {
            ShopListRow("Low stock: $lowStock ${if (lowStock == 1) "product" else "products"}",
                supporting = "Positive stock at or below the set threshold",
                leading = { ShopStatusIconTile(R.drawable.ic_warning, ShopStatusTone.Warning) },
                onClick = { onStock(StockHealth.LowStock) })
            ShopListRow("Out of stock: $outOfStock ${if (outOfStock == 1) "product" else "products"}",
                supporting = "No pieces currently on hand",
                leading = { ShopStatusIconTile(R.drawable.ic_no_stock, ShopStatusTone.Danger) },
                onClick = { onStock(StockHealth.OutOfStock) })
        }
    }
    ShopSectionHeader(stringResource(R.string.home_recent_sales), actionLabel = if (shop.sales.isNotEmpty()) "View all" else null, onAction = onSales)
    if (recentSales.isEmpty()) Text(stringResource(R.string.home_no_recent_sales), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    else ShopListGroup {
        recentSales.forEach { sale ->
            ShopListRow("Cart ${sale.cartNumber}",
                supporting = listOfNotNull(sale.customer.ifBlank { null }, "${sale.lines.size} lines").joinToString(" · "),
                meta = "₹${indianNumber(sale.totalRupees)}", status = { ShopStatusChip(stringResource(R.string.shared_paid), ShopStatusTone.Success) },
                actionLabel = "Open receipt · Cart ${sale.cartNumber}", onClick = { onSale(sale.cartId) })
        }
    }
}

@Composable
internal fun StockScreen(shop: ShopSnapshot, onAdd: () -> Unit, onOpen: (String) -> Unit, loadPhoto: PhotoLoader, initialHealth: String? = null, busy: Boolean = false,
    onScan: () -> Unit = {}, onProductAction: (Product, String) -> Unit = { _, _ -> }) {
    if (shop.products.none { !it.archived }) {
        if (shop.products.isEmpty()) ShopEmptyState(stringResource(R.string.stock_no_products_title), "Add a product to start tracking your stock.", R.drawable.ic_stock)
        else ShopEmptyState(stringResource(R.string.home_no_active_title), "Add a product for current work. Archived products and their history are available under More.", R.drawable.ic_stock)
    } else {
        ShopPageHeader(stringResource(R.string.stock_find_product))
        StockSearch(shop, initialHealth, loadPhoto, onOpen, busy, onScan, onProductAction)
    }
}
@Composable
internal fun ProductDetails(shop: ShopSnapshot, product: Product, onEdit: () -> Unit, onManage: () -> Unit, loadPhoto: PhotoLoader,
    busy: Boolean, onCopy: () -> Unit, onTemplate: () -> Unit, onCorrection: (CorrectionKind) -> Unit, onArchive: () -> Unit,
    archiveError: String?, onReceive: () -> Unit, onLabels: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // At large system text the photo would squeeze the title into a narrow column.
    val stackHeader = LocalDensity.current.fontScale >= 1.5f
    val summary: @Composable ColumnScope.() -> Unit = {
        Text(product.title, style = MaterialTheme.typography.titleLarge)
        Text(shop.categories.single { it.id == product.categoryId }.name, style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.pieces, indianNumber(product.quantity)), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.selling_price, indianNumber(product.priceRupees)), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        if (product.archived) Text(stringResource(R.string.product_archived_label), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else StockHealthIndicator(product)
    }
    Surface(shape = MaterialTheme.shapes.medium, color = colors.primary.copy(alpha = .08f).compositeOver(colors.surface)) {
        if (stackHeader) Column(Modifier.fillMaxWidth().padding(ShopSpacing.content),
            verticalArrangement = Arrangement.spacedBy(ShopSpacing.medium)) {
            ProductPhoto(product.photo, loadPhoto, Modifier.size(96.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = summary)
        } else Row(Modifier.fillMaxWidth().padding(ShopSpacing.content), horizontalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
            ProductPhoto(product.photo, loadPhoto, Modifier.size(104.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = summary)
        }
    }
    if (!product.archived) ShopPrimaryButton(stringResource(R.string.product_edit_details), enabled = !busy, onClick = onEdit)
    if (shop.missingRequired(product.id).isNotEmpty()) Text(stringResource(R.string.product_catalog_incomplete), style = MaterialTheme.typography.bodyMedium)
    if (!product.archived) {
        ShopSectionHeader(stringResource(R.string.product_correct_stock))
        ShopListGroup {
            CorrectionKind.entries.forEach { kind ->
                ShopListRow("Record ${kind.label.lowercase()}",
                    supporting = when (kind) {
                        CorrectionKind.PhysicalCount -> "Enter the total pieces counted"
                        CorrectionKind.Damage -> "Remove damaged pieces from stock"
                        CorrectionKind.Loss -> "Remove lost pieces from stock"
                    },
                    icon = R.drawable.ic_inventory, enabled = !busy, onClick = { onCorrection(kind) })
            }
        }
    }
    ShopSection(stringResource(R.string.product_details_section)) {
        Text(product.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodyLarge)
        shop.attributes.filter { it.appliesTo(product.categoryId, product.id) && (!it.retired || it.id in product.attributes) }.forEach { definition ->
            val value = product.attributes[definition.id]
            val display = if (value == null && definition.required && !definition.retired) "Required · missing" else attributeDisplay(shop, value)
            Text(definition.name + (if (definition.retired) " (retired)" else "") + ": " + display + (if (value != null && definition.unit.isNotBlank()) " " + definition.unit else ""), style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = colors.outlineVariant)
        Text(stringResource(R.string.sku, product.sku), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.opening_stock_pieces, indianNumber(shop.openings[product.id]?.quantity ?: 0L)), style = MaterialTheme.typography.bodyMedium)
    }
    if (product.historicalAttributes.isNotEmpty()) ShopSection(stringResource(R.string.product_historical_attributes)) {
        product.historicalAttributes.forEach { (_, definitionId, value) ->
            val definition = shop.attributes.single { it.id == definitionId }
            Text(definition.name + ": " + attributeDisplay(shop, value) + " " + definition.unit, style = MaterialTheme.typography.bodyMedium)
        }
    }
    CorrectionHistory(shop.corrections.filter { it.productId == product.id })
    FindingSummary(shop, product)
    if (product.archived) ShopListRow(stringResource(R.string.product_preview_labels), supporting = "Package or shared-bin label",
        icon = R.drawable.ic_template, enabled = !busy, onClick = onLabels)
    if (!product.archived) {
        ShopSectionHeader(stringResource(R.string.product_actions))
        ShopListGroup {
            ShopListRow(stringResource(R.string.product_receive_stock), supporting = "Add received pieces and purchase cost",
                icon = R.drawable.ic_inventory, enabled = !busy, onClick = onReceive)
            ShopListRow(stringResource(R.string.product_preview_labels), supporting = "Package or shared-bin label",
                icon = R.drawable.ic_template, enabled = !busy, onClick = onLabels)
            ShopListRow(stringResource(R.string.scanner_copy_catalog), supporting = "Start a new product with these details",
                icon = R.drawable.ic_add, enabled = !busy, onClick = onCopy)
            ShopListRow(stringResource(R.string.product_create_template), supporting = "Reuse these details later",
                icon = R.drawable.ic_template, enabled = !busy, onClick = onTemplate)
            ShopListRow(stringResource(R.string.product_manage_fields), supporting = "Custom and category fields",
                icon = R.drawable.ic_settings, enabled = !busy, onClick = onManage)
        }
        ShopSection(stringResource(R.string.product_archive_section)) {
            Text(stringResource(R.string.product_archive_desc), style = MaterialTheme.typography.bodyMedium)
            if (archiveError != null) FormError(archiveError)
            ShopSecondaryButton(onClick = onArchive, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.product_archive_btn)) }
        }
    }
}

@Composable
internal fun ArchivedProducts(shop: ShopSnapshot, loadPhoto: PhotoLoader, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable(query) { mutableIntStateOf(40) }
    val archived = shop.products.filter { it.archived }
    if (archived.isEmpty()) ShopEmptyState(stringResource(R.string.archived_no_products_title), "Products archived at zero stock remain here with their details and history.", R.drawable.ic_stock)
    else {
        ShopSearchField(query, { query = it }, "Search archived products")
        val found = archived.filter { LookupNames.key(it.title + " " + it.sku).contains(LookupNames.key(query)) }
        ShopSectionHeader("${found.size} archived products", supporting = "Title or permanent SKU")
        if (found.isEmpty()) ShopEmptyState(stringResource(R.string.archived_no_matching_title), "Try a title or permanent SKU.", R.drawable.ic_stock)
        else ShopListGroup {
            found.take(limit).forEach { product -> StockProductRow(shop, product, loadPhoto) { onOpen(product.id) } }
        }
        if (found.size > limit) ShopSecondaryButton(onClick = { limit += 40 }, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.archived_show_more)) }
    }
}

@Composable
internal fun MoreScreen(shop: ShopSnapshot, appearance: Appearance, busy: Boolean, onAppearance: (Appearance) -> Unit,
    onCategories: () -> Unit, onFindingLookups: () -> Unit, onArchived: () -> Unit, onSales: () -> Unit, onTemplates: () -> Unit, onBackups: () -> Unit, updates: @Composable () -> Unit) {
    ShopPageHeader(stringResource(R.string.more_header), "Catalog, appearance and recovery.")
    ShopSectionHeader(stringResource(R.string.more_appearance))
    ShopGroup(spacing = ShopSpacing.small) {
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)
                    .selectable(appearance == mode, enabled = !busy, role = Role.RadioButton, onClick = { onAppearance(mode) })
                    .padding(horizontal = ShopSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = appearance == mode, onClick = null)
                    Text(mode.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = ShopSpacing.medium))
                }
            }
        }
        Text(stringResource(R.string.more_appearance_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    updates()
    ShopSectionHeader(stringResource(R.string.more_shop))
    ShopListGroup {
        ShopListRow(stringResource(R.string.more_sales_history), supporting = "Completed cash sales and receipts", icon = R.drawable.ic_receipt,
            enabled = !busy, onClick = onSales)
        ShopListRow(stringResource(R.string.more_product_templates), supporting = "Reusable catalog details", icon = R.drawable.ic_template,
            enabled = !busy, onClick = onTemplates)
        ShopListRow(stringResource(R.string.more_archived_products), supporting = "Retained details and history", icon = R.drawable.ic_archive,
            enabled = !busy, onClick = onArchived)
    }
    ShopSectionHeader(stringResource(R.string.more_backup_recovery))
    ShopListGroup {
        ShopListRow(stringResource(R.string.more_shop_backups), supporting = "Drive, local copies and phone recovery", icon = R.drawable.ic_cloud,
            enabled = !busy, onClick = onBackups)
    }
    ShopSectionHeader(stringResource(R.string.more_catalog))
    ShopListGroup {
        ShopListRow(stringResource(R.string.more_edit_categories), supporting = "${shop.categories.size} categories", icon = R.drawable.ic_category,
            enabled = !busy, onClick = onCategories)
        ShopListRow(stringResource(R.string.more_manage_fitment), supporting = "${shop.makes.size} makes · ${shop.locations.size} locations",
            icon = R.drawable.ic_two_wheeler, enabled = !busy, onClick = onFindingLookups)
    }
}

@Composable
internal fun CategorySettings(shop: ShopSnapshot, busy: Boolean, onRename: (String, String) -> Unit) {
    Text(stringResource(R.string.category_rename_hint), style = MaterialTheme.typography.bodyMedium)
    ShopListGroup {
        shop.categories.forEach { category ->
            key(category.id) {
                var editing by rememberSaveable(category.id) { mutableStateOf(false) }
                var name by rememberSaveable(category.name) { mutableStateOf(category.name) }
                val productCount = shop.products.count { it.categoryId == category.id && !it.archived }
                ShopListRow(category.name, supporting = "$productCount active products", icon = R.drawable.ic_category,
                    trailing = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
                    showChevron = false, enabled = !busy, actionLabel = "Rename ${category.name}",
                    onClick = { editing = true })
                if (editing) AlertDialog(
                    onDismissRequest = { if (!busy) editing = false },
                    title = { Text(stringResource(R.string.rename, category.name)) },
                    text = { OutlinedTextField(name, { name = it }, enabled = !busy,
                        label = { Text(stringResource(R.string.category_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = {
                        TextButton(enabled = !busy && name != category.name, onClick = {
                            editing = false
                            onRename(category.id, name)
                        }) { Text(stringResource(R.string.save, category.name)) }
                    },
                    dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) } },
                )
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
    var price by rememberSaveable { mutableStateOf(product?.priceRupees?.toString() ?: "") }
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
    if (templateMode || initialCatalog != null) Text(stringResource(R.string.form_catalog_only_note), style = MaterialTheme.typography.bodyMedium)
    PhotoField(product?.photo ?: initialCatalog?.photo, photoUri, photoRemoved, busy, loadPhoto, error?.takeIf { it.field == ProductField.Photo }?.message,
        select = { photoUri = it; photoRemoved = false; onChange() },
        remove = { photoUri = null; photoRemoved = true; onChange() })
    ShopSection(stringResource(R.string.form_basic_details)) {
        ProductTextField(title, { title = it; distinct = false; onChange() }, "Title (required)", !busy, error?.takeIf { it.field == ProductField.Title })
        Text(stringResource(R.string.form_category_required), style = MaterialTheme.typography.labelLarge)
        LookupSelector("Search or add category", "Add category", shop.categories.map { it.id to it.name }, setOf(category), busy,
            onSelect = { category = it; mappings = emptyMap(); distinct = false; onChange() },
            onCreate = { name -> lookups.category(name) { category = it; mappings = emptyMap(); distinct = false; onChange() } })
        if (error?.field == ProductField.Category) FormError(error.message)
        ProductTextField(description, { description = it; onChange() }, "Description", !busy)
    }
    if (product != null && !product.archived && !templateMode) ShopSection(stringResource(R.string.form_selling_price)) {
        ProductTextField(price, { price = it; onChange() }, "Selling price (whole rupees)", !busy,
            error?.takeIf { it.field == ProductField.SellingPrice }, number = true)
        Text(stringResource(R.string.form_price_update_note), style = MaterialTheme.typography.bodyMedium)
    }
    if (templateMode && initialCatalog != null && initialCatalog.categoryId.isNotEmpty() && category != initialCatalog.categoryId) Text(stringResource(R.string.form_prev_category_note), style = MaterialTheme.typography.bodyMedium)
    if (product != null && category != product.categoryId) CategoryMapping(shop, product, category, mappings, busy) { mappings = it }
    if (category.isNotEmpty()) CategoryFields(formShop, category, values, busy, error, formLookups, if (product != null) product.id else "catalog-draft",
        onValue = { id, value -> values = values.toMutableMap().apply { if (value == null) remove(id) else put(id, value) }; onChange() })
    if (product == null) DraftCustomFields(customFields, category, busy, error, ::updateCustom)
    FindingFields(shop, product, models, locations, busy, findingError ?: error, findingLookups, assigned,
        onAssigned = { assigned = it; findingError = null; onChange() },
        onModels = { models = it; findingError = null; onChange() }, onLocations = { locations = it; onChange() }, showLocations = !templateMode)
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
        Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget).toggleable(distinct, enabled = !busy, role = Role.Checkbox, onValueChange = { distinct = it }),
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
        val applicable = values.filterKeys { id -> formShop.attributes.any { it.id == id && it.appliesTo(category, product?.id ?: "catalog-draft") } }
        save(ProductFormInput(CatalogDraft(title, category, description, applicable, customFields, models, initialCatalog?.photo),
            quantity, price, distinct, mappings, locations, photoUri, photoRemoved))
    }
}

@Composable
private fun ProductTextField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, error: ShopError? = null, number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(value, onChange, enabled = enabled, label = { Text(label) }, isError = error != null,
        supportingText = if (error == null) null else ({ FormError(error.message) }),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = modifier)
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
    ShopSection(stringResource(R.string.category_change)) {
        Text(stringResource(R.string.previous_category_values_will_be_kept_as), style = MaterialTheme.typography.bodyMedium)
        shop.attributes.filter { it.productId == null && it.categoryId == product.categoryId && it.id in product.attributes }.forEach { source ->
            val targets = shop.attributes.filter { it.productId == null && it.categoryId == categoryId && !it.retired && it.type == source.type && it.unit == source.unit && !it.type.isChoice }
            if (targets.isNotEmpty()) {
                Text(stringResource(R.string.copy) + source.name, style = MaterialTheme.typography.labelLarge)
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
