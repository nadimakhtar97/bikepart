package com.nadim.bikepart.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopViewModel
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.BikepartTheme
import com.nadim.bikepart.ui.theme.ShopSpacing

private data class MainDestination(val label: String, val outlined: Int, val filled: Int)
private val destinations = listOf(
    MainDestination("Home", R.drawable.ic_home, R.drawable.ic_home_filled),
    MainDestination("Stock", R.drawable.ic_stock, R.drawable.ic_stock_filled),
    MainDestination("Carts", R.drawable.ic_carts, R.drawable.ic_carts_filled),
    MainDestination("More", R.drawable.ic_more, R.drawable.ic_more_filled),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopApp(model: ShopViewModel = viewModel(), camera: ScanCameraContent = { result, failed -> ScanCamera(result, failed) }) {
    val stockState = rememberSaveableStateHolder()
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val cartDraft by model.cartDraft.collectAsStateWithLifecycle()
    var stockHealth by rememberSaveable { mutableStateOf<String?>(null) }
    var stockSession by rememberSaveable { mutableIntStateOf(0) }
    var destination by rememberSaveable { mutableStateOf("Home") }
    var route by rememberSaveable { mutableStateOf("main") }
    var correctionKind by rememberSaveable { mutableStateOf(CorrectionKind.PhysicalCount.name) }
    var correctionOperation by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var scanPurpose by rememberSaveable { mutableStateOf(ScanPurpose.Catalog.name) }
    var scanReturn by rememberSaveable { mutableStateOf("create") }
    var arrivalOperation by rememberSaveable { mutableStateOf("") }
    fun scan(purpose: ScanPurpose, returnRoute: String) { scanPurpose = purpose.name; scanReturn = returnRoute; model.error.value = null; route = "scan" }
    fun receive(id: String) { selectedId = id; arrivalOperation = java.util.UUID.randomUUID().toString(); model.error.value = null; route = "arrival" }
    var checkoutVersion by rememberSaveable { mutableLongStateOf(0L) }
    // A persisted cart identity also recovers a committed receipt after a lost checkout response.
    val selectedSale = state?.sales?.find { it.cartId == selectedId }
    LaunchedEffect(state?.revision, route, selectedId) {
        if (route in listOf("cart", "checkout") && selectedSale != null) { route = "receipt"; model.error.value = null }
    }
    LaunchedEffect(state != null, route) {
        if (state != null && (route in listOf("cart", "cart-products") || route == "scan" && scanPurpose == ScanPurpose.Sale.name) && cartDraft == null) model.openCart(selectedId)
    }
    var catalogPayload by rememberSaveable { mutableStateOf<String?>(null) }
    var formSession by rememberSaveable { mutableIntStateOf(0) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = catalogPayload?.let { decodeCatalog(it) }
    val template = state?.templates?.find { it.id == templateId }
    fun beginCatalog(draft: CatalogDraft, target: String, id: String? = null) { model.error.value = null; formSession++; catalogPayload = draft.encode(); templateId = id; route = target }
    val product = state?.products?.find { it.id == selectedId }
    fun back() {
        if (route == "scan") { route = scanReturn; model.error.value = null; return }
        if (route == "cart") { model.flushCart { route = "main"; destination = "Carts" }; return }
        route = when (route) { "checkout", "cart-products" -> "cart"; "receipt" -> "sales"; "detail" -> if (product?.archived == true) "archived-products" else "main"; "edit", "fields", "correction" -> "detail"; "arrival" -> "receive-products"; "template-detail", "template-form" -> "templates"; else -> "main" }
        model.error.value = null
    }
    fun newCart() { model.write({ selectedId = createCart() }, { model.openCart(selectedId); destination = "Carts"; route = "cart" }) }
    fun addProduct() { model.error.value = null; formSession++; catalogPayload = null; route = "create" }
    fun openStock(health: StockHealth?) {
        stockState.removeState("stock:$stockSession")
        stockSession++
        stockHealth = health?.name
        destination = "Stock"
        model.error.value = null
    }
    BackHandler(route != "main") { if (!busy) back() }
    BikepartTheme(appearance = state?.appearance ?: Appearance.System) {
        val colors = MaterialTheme.colorScheme
        Scaffold(
            containerColor = colors.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(when (route) {
                        "scan" -> "Scan shared label"; "receive-products", "arrival" -> "Receive stock"; "catalog-search" -> "Find catalog details"
                        "templates" -> "Product templates"; "template-detail" -> "Template"; "template-form" -> if (templateId == null) "New template" else "Edit template"; "create" -> "New product"; "detail" -> "Product"; "edit" -> "Edit details"
                        "archived-products" -> "Archived products"; "checkout" -> "Cash checkout"; "receipt" -> "Receipt"; "sales" -> "Sales history"; "cart" -> "Unfinished bill"; "cart-products" -> "Add to cart"; "correction" -> CorrectionKind.valueOf(correctionKind).label; "finding-lookups" -> "Fitment and locations"; "categories" -> "Categories"; "fields" -> "Catalog fields"; else -> destination
                    }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (route != "main") TextButton(enabled = !busy, onClick = { back() }) { Text("Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface),
                )
            },
            bottomBar = {
                if (route == "main") NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                    destinations.forEach { item ->
                        val selected = destination == item.label
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (item.label == "Stock") openStock(null) else { destination = item.label; model.error.value = null } },
                            icon = { Icon(painterResource(if (selected) item.filled else item.outlined), contentDescription = null) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = colors.primaryContainer, selectedIconColor = colors.onPrimaryContainer,
                                selectedTextColor = colors.primary, unselectedIconColor = colors.onSurface,
                                unselectedTextColor = colors.onSurface,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            val scrollState = key(route, destination, selectedId) { rememberScrollState() }
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = ShopSpacing.formWidth).fillMaxWidth().verticalScroll(scrollState).padding(ShopSpacing.content),
                    verticalArrangement = Arrangement.spacedBy(ShopSpacing.section),
                ) {
                    val shop = state
                    if (shop == null) { Text(error?.message ?: "Opening your shop…"); return@Column }
                    when (route) {
                        "scan" -> ContextualScanner(shop, ScanPurpose.valueOf(scanPurpose), busy, error, camera,
                            manual = { route = when (ScanPurpose.valueOf(scanPurpose)) { ScanPurpose.Sale -> "cart-products"; ScanPurpose.Receiving -> "receive-products"; ScanPurpose.Catalog -> "catalog-search" } },
                            onChange = { model.error.value = null },
                            add = { id, pieces -> cartDraft?.let { draft -> model.write({ addCartProduct(draft.cart.id, draft.cart.editVersion, id, pieces) }, { model.openCart(draft.cart.id); route = "cart" }) } },
                            receive = ::receive, open = { selectedId = it; route = "detail" }, copy = { beginCatalog(it, "create") }, cart = cartDraft?.cart)
                        "receive-products", "catalog-search" -> {
                            val forCatalog = route == "catalog-search"
                            OutlinedButton(onClick = { scan(if (forCatalog) ScanPurpose.Catalog else ScanPurpose.Receiving, route) }, enabled = !busy) { Text(if (forCatalog) "Scan catalog details" else "Scan for receiving") }
                            ScanManualPicker(shop, forCatalog, busy, model::readPhoto,
                                select = { if (forCatalog) { selectedId = it; route = "detail" } else receive(it) },
                                template = { beginCatalog(shop.prefillTemplate(it), "create") }, create = { route = "create" })
                        }
                        "arrival" -> if (product != null && !product.archived) key(arrivalOperation) {
                            StockArrivalForm(product, busy, error, onChange = { model.error.value = null },
                                save = { pieces, cost -> model.write({ receiveStock(arrivalOperation, product.id, pieces, cost) }, { route = "detail" }) })
                        }
                        "cart" -> CartEditor(shop, cartDraft, busy, error,
                            edit = model::editCart, pause = ::back, add = { route = "cart-products" },
                            retry = { model.flushCart() }, reload = model::reloadCart,
                            checkout = { model.flushCart { checkoutVersion = requireNotNull(model.cartDraft.value).cart.editVersion; model.error.value = null; route = "checkout" } },
                            remove = { productId -> cartDraft?.let { draft ->
                                model.write({ saveCart(draft.cart.id, draft.cart.editVersion, draft.customer, draft.quantities - productId) }, { model.openCart(draft.cart.id) })
                            } },
                            discard = { cartDraft?.let { draft -> model.write({ discardCart(draft.cart.id, draft.cart.editVersion) }, { route = "main"; destination = "Carts" }) } })
                        "checkout" -> {
                            val cart = shop.carts.find { it.id == selectedId }
                            if (selectedSale != null) SaleReceipt(selectedSale) { model.error.value = null; route = "sales" }
                            else if (cart != null) CashCheckout(shop, cart, busy, error,
                                onChange = { model.error.value = null }, edit = { model.openCart(cart.id); route = "cart" },
                                complete = { cash ->
                                    model.write({ checkout(cart.id, checkoutVersion, cash) }, { route = "receipt" })
                                })
                            else Text("This cart is no longer available. Return to the cart list.")
                        }
                        "sales" -> SalesHistory(shop) { selectedId = it; model.error.value = null; route = "receipt" }
                        "receipt" -> if (selectedSale != null) SaleReceipt(selectedSale) { model.error.value = null; route = "sales" }
                            else Text("Receipt unavailable. Return to Sales history.")
                        "cart-products" -> cartDraft?.let { draft ->
                            OutlinedButton(onClick = { scan(ScanPurpose.Sale, "cart-products") }, enabled = !busy) { Text("Scan for sale") }
                            CartProductPicker(shop, shop.carts.find { it.id == draft.cart.id } ?: draft.cart, busy, error, model::readPhoto,
                                onChange = { model.error.value = null }, add = { productId, pieces ->
                                    model.write({ addCartProduct(draft.cart.id, draft.cart.editVersion, productId, pieces) }, { model.openCart(draft.cart.id); route = "cart" })
                                })
                        }
                        "create", "edit", "template-form" -> stockState.SaveableStateProvider("form:$formSession:$route:$selectedId:$catalogPayload") { key(route, selectedId, catalogPayload) {
                            if (route == "create") OutlinedButton(onClick = { scan(ScanPurpose.Catalog, "create") }, enabled = !busy) { Text("Scan catalog details") }
                            if (route == "create" && catalog == null) OutlinedButton(onClick = { route = "templates" }, enabled = !busy) { Text("Choose template") }
                            ProductForm(shop, if (route == "edit") product else null, busy, error,
                                onChange = { model.error.value = null },
                                lookups = ProductLookups(
                                    category = { name, selected -> model.write({ val id = createCategory(name); selected(id) }) },
                                    attribute = { input, done -> model.write({ createAttribute(categoryId = input.categoryId, name = input.name, type = input.type, required = input.required, unit = input.unit) }, done) },
                                    option = { definition, name, selected -> model.write({ val id = createOption(definition, name); selected(id) }) },
                                ),
                                findingLookups = FindingLookups(
                                    make = { name, selected -> model.write({ selected(createMake(name)) }) },
                                    model = { make, name, selected -> model.write({ selected(createModel(make, name)) }) },
                                    location = { name, selected -> model.write({ selected(createLocation(name)) }) },
                                ),
                                loadPhoto = model::readPhoto,
                                initialCatalog = if (route == "edit") null else catalog,
                                templateMode = route == "template-form",
                                save = { input ->
                                    val editing = if (route == "edit") product else null
                                    val photo = input.photoUri?.let { uri -> com.nadim.bikepart.shop.PhotoChange.Import {
                                        resolver.openInputStream(uri.toUri()) ?: throw com.nadim.bikepart.shop.ShopValidation(com.nadim.bikepart.shop.ProductField.Photo, "Photo unavailable. Choose it again or remove it.")
                                    } } ?: if (input.photoRemoved) com.nadim.bikepart.shop.PhotoChange.Remove else com.nadim.bikepart.shop.PhotoChange.Keep
                                    model.write({
                                        if (route == "template-form") templateId = saveTemplate(input.catalog, templateId, photo)
                                        else if (editing != null) editProduct(editing.id, input.catalog.title, input.catalog.categoryId, input.catalog.description, input.catalog.attributes, input.categoryMappings, input.catalog.modelIds, input.locationIds, photo)
                                        else selectedId = createFromCatalog(input.catalog, input.openingPieces, input.priceRupees, input.allowSimilar, input.locationIds, photo)
                                    }, { route = if (route == "template-form") "template-detail" else "detail" })
                                },
                            )
                        }
                        }
                        "templates" -> TemplateList(shop, busy,
                            onCreate = { beginCatalog(CatalogDraft(), "template-form") },
                            onOpen = { templateId = it; route = "template-detail" },
                            onUse = { beginCatalog(shop.prefillTemplate(it), "create") })
                        "template-detail" -> if (template != null) TemplateDetails(shop, template, busy, model::readPhoto,
                            onEdit = { beginCatalog(template.catalog, "template-form", template.id) },
                            onUse = { beginCatalog(shop.prefillTemplate(template.id), "create") },
                            onRetire = { model.write({ retireTemplate(template.id) }) })
                        "correction" -> if (product != null) key(correctionOperation) {
                            StockCorrectionForm(product, CorrectionKind.valueOf(correctionKind), busy, error,
                                onChange = { model.error.value = null },
                                save = { quantity, reason -> model.write({ correctStock(correctionOperation, product.id, CorrectionKind.valueOf(correctionKind), quantity, reason) }, { route = "detail" }) })
                        }
                        "archived-products" -> ArchivedProducts(shop, model::readPhoto) { selectedId = it; route = "detail" }
                        "detail" -> if (product != null) {
                            if (!product.archived) OutlinedButton(onClick = { receive(product.id) }, enabled = !busy) { Text("Receive stock") }
                            ProductDetails(shop, product, onEdit = { route = "edit" }, onManage = { route = "fields" }, loadPhoto = model::readPhoto, busy = busy, onCopy = { beginCatalog(shop.copyCatalog(product.id), "create") }, onTemplate = { beginCatalog(shop.copyCatalog(product.id), "template-form") }, onCorrection = { kind ->
                            model.error.value = null; correctionKind = kind.name; correctionOperation = java.util.UUID.randomUUID().toString(); route = "correction"
                        }, onArchive = { model.write({ archiveProduct(product.id) }) }, archiveError = error?.takeIf { it.field == null }?.message)
                            if (!product.archived) ThresholdForm(product, busy, error, onChange = { model.error.value = null },
                                save = { pieces -> model.write({ setLowStockThreshold(product.id, pieces) }) })
                            SaleStockHistory(shop, product.id)
                            ArrivalHistory(shop.arrivals.filter { it.productId == product.id })
                        }
                        "fields" -> if (product != null) CatalogManagement(shop, product, busy, error, { model.error.value = null }, CatalogActions(
                            custom = { input, done -> model.write({ createCustomAttribute(product.id, input.name, input.type, input.required, input.unit) }, done) },
                            promote = { id, name -> model.write({ promoteCustomAttribute(id, name) }) },
                            retire = { id -> model.write({ retireAttribute(id) }) },
                            retireOption = { id -> model.write({ retireOption(id) }) },
                            required = { id, required -> model.write({ setAttributeRequired(id, required) }) },
                            replace = { id, input, done -> model.write({ replaceAttribute(id, input.name, input.type, input.required, input.unit) }, done) },
                        ))
                        "finding-lookups" -> FindingLookupSettings(shop, busy,
                            retireMake = { model.write({ retireMake(it) }) },
                            retireModel = { model.write({ retireModel(it) }) },
                            retireLocation = { model.write({ retireLocation(it) }) })
                        "categories" -> CategorySettings(shop, busy, onRename = { id, name -> model.write({ renameCategory(id, name) }) })
                        else -> when (destination) {
                            "Home" -> {
                                HomeScreen(shop, onAdd = { addProduct() }, onStock = ::openStock, onNewSale = ::newCart, onCarts = { destination = "Carts" }, busy = busy)
                                OutlinedButton(onClick = { route = "receive-products" }, enabled = !busy) { Text("Receive stock") }
                            }
                            "Stock" -> stockState.SaveableStateProvider("stock:$stockSession") {
                                StockScreen(shop, onAdd = { addProduct() }, onOpen = { selectedId = it; route = "detail" }, loadPhoto = model::readPhoto, initialHealth = stockHealth)
                            }
                            "Carts" -> CartList(shop, busy, ::newCart, onOpen = { selectedId = it; model.openCart(it); route = "cart" })
                            "More" -> {
                                OutlinedButton(onClick = { route = "archived-products" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Archived products") }
                                OutlinedButton(onClick = { route = "sales" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Sales history") }
                                OutlinedButton(onClick = { route = "templates" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Product templates") }
                                AppearanceSettings(shop.appearance, busy,
                                onAppearance = { model.write({ setAppearance(it) }) }, onCategories = { route = "categories" }, onFindingLookups = { route = "finding-lookups" })
                            }
                        }
                    }
                    if (error != null && route != "scan" && route != "arrival" && route != "receipt" && route != "checkout" && route != "cart" && route != "cart-products" && !(route == "detail" && (error?.field == ProductField.LowStockThreshold || error?.field == null)) && route != "correction" && route != "create" && route != "edit" && route != "template-form" &&
                        !(route == "fields" && error?.field in listOf(com.nadim.bikepart.shop.ProductField.DefinitionName, com.nadim.bikepart.shop.ProductField.DefinitionType))) FormError(requireNotNull(error).message)
                }
            }
        }
    }
}
