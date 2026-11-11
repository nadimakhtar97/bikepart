package com.nadim.bikepart.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun ShopApp(model: ShopViewModel = viewModel()) {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf("Home") }
    var route by rememberSaveable { mutableStateOf("main") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var catalogPayload by rememberSaveable { mutableStateOf<String?>(null) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = catalogPayload?.let { decodeCatalog(it) }
    val template = state?.templates?.find { it.id == templateId }
    fun beginCatalog(draft: CatalogDraft, target: String, id: String? = null) { model.error.value = null; catalogPayload = draft.encode(); templateId = id; route = target }
    val product = state?.products?.find { it.id == selectedId }
    fun back() { route = when (route) { "edit", "fields" -> "detail"; "template-detail", "template-form" -> "templates"; else -> "main" }; model.error.value = null }
    fun addProduct() { model.error.value = null; catalogPayload = null; route = "create" }
    BackHandler(route != "main") { if (!busy) back() }
    BikepartTheme(appearance = state?.appearance ?: Appearance.System) {
        val colors = MaterialTheme.colorScheme
        Scaffold(
            containerColor = colors.surface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(when (route) {
                        "templates" -> "Product templates"; "template-detail" -> "Template"; "template-form" -> if (templateId == null) "New template" else "Edit template"; "create" -> "New product"; "detail" -> "Product"; "edit" -> "Edit details"
                        "finding-lookups" -> "Fitment and locations"; "categories" -> "Categories"; "fields" -> "Catalog fields"; else -> destination
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
                            onClick = { destination = item.label; model.error.value = null },
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
                        "create", "edit", "template-form" -> key(route, selectedId, catalogPayload) {
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
                        "templates" -> TemplateList(shop, busy,
                            onCreate = { beginCatalog(CatalogDraft(), "template-form") },
                            onOpen = { templateId = it; route = "template-detail" },
                            onUse = { beginCatalog(shop.prefillTemplate(it), "create") })
                        "template-detail" -> if (template != null) TemplateDetails(shop, template, busy, model::readPhoto,
                            onEdit = { beginCatalog(template.catalog, "template-form", template.id) },
                            onUse = { beginCatalog(shop.prefillTemplate(template.id), "create") },
                            onRetire = { model.write({ retireTemplate(template.id) }) })
                        "detail" -> if (product != null) ProductDetails(shop, product, onEdit = { route = "edit" }, onManage = { route = "fields" }, loadPhoto = model::readPhoto, busy = busy, onCopy = { beginCatalog(shop.copyCatalog(product.id), "create") }, onTemplate = { beginCatalog(shop.copyCatalog(product.id), "template-form") })
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
                            "Home" -> HomeScreen(shop, onAdd = { addProduct() }, onStock = { destination = "Stock" })
                            "Stock" -> StockScreen(shop, onAdd = { addProduct() }, onOpen = { selectedId = it; route = "detail" }, loadPhoto = model::readPhoto)
                            "Carts" -> ShopEmptyState("No unfinished carts", "Your unfinished customer bills will appear here.", R.drawable.ic_carts)
                            "More" -> {
                                OutlinedButton(onClick = { route = "templates" }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = ShopSpacing.touchTarget)) { Text("Product templates") }
                                AppearanceSettings(shop.appearance, busy,
                                onAppearance = { model.write({ setAppearance(it) }) }, onCategories = { route = "categories" }, onFindingLookups = { route = "finding-lookups" })
                            }
                        }
                    }
                    if (error != null && route != "create" && route != "edit" && route != "template-form" &&
                        !(route == "fields" && error?.field in listOf(com.nadim.bikepart.shop.ProductField.DefinitionName, com.nadim.bikepart.shop.ProductField.DefinitionType))) FormError(requireNotNull(error).message)
                }
            }
        }
    }
}

