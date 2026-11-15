package com.nadim.bikepart.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.nadim.bikepart.updates.*
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
fun ShopApp(model: ShopViewModel = viewModel(), camera: ScanCameraContent = { result, failed -> ScanCamera(result, failed) },
    labelUsb: (android.content.Context, () -> Boolean) -> com.nadim.bikepart.printing.LabelUsbAccess = { context, cancelled ->
        com.nadim.bikepart.printing.UsbLabelPrinter(context, cancelled)
    },
    updater: @Composable () -> AppUpdater = { viewModel<UpdateViewModel>().updater },
    reminders: @Composable () -> UpdateReminders = { viewModel<UpdateViewModel>().reminders },
    installer: @Composable () -> AppInstaller = { viewModel<UpdateViewModel>().installer }) {
    val updateService = updater()
    val reminderService = reminders()
    val installService = installer()
    val updateState by updateService.state.collectAsStateWithLifecycle()
    val downloadState by updateService.downloadState.collectAsStateWithLifecycle()
    val updateScope = rememberCoroutineScope()
    var updateJob by remember { mutableStateOf<Job?>(null) }
    val invalidApkMsg = stringResource(R.string.update_install_invalid)
    val permissionNeededMsg = stringResource(R.string.update_install_permission_needed)
    val installFailedMsg = stringResource(R.string.update_install_failed)
    var installError by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, updateService, reminderService) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (reminderService.shouldCheckAutomatically() && updateJob?.isActive != true) {
                    reminderService.recordCheckAttempt()
                    updateJob = updateScope.launch {
                        try { updateService.check() } catch (_: Exception) {}
                    }
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                updateJob?.cancel()
                // Rotation and in-app navigation keep a transfer; only actual
                // backgrounding stops it. Process death leaves temporary files.
                if (context.findActivity()?.isChangingConfigurations != true) updateService.cancelDownload()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); updateJob?.cancel() }
    }
    val stockState = rememberSaveableStateHolder()
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val cartDraft by model.cartDraft.collectAsStateWithLifecycle()
    val localCapture by model.localCapture.collectAsStateWithLifecycle()
    val recoveryCapture by model.recoveryCapture.collectAsStateWithLifecycle()
    val backupBusy by model.backupBusy.collectAsStateWithLifecycle()
    val backupError by model.backupError.collectAsStateWithLifecycle()
    val backupSaved by model.backupSaved.collectAsStateWithLifecycle()
    val exportedCaptureId by model.exportedCaptureId.collectAsStateWithLifecycle()
    val recoveryExport = exportedCaptureId != null && exportedCaptureId == recoveryCapture?.id
    var stockHealth by rememberSaveable { mutableStateOf<String?>(null) }
    var stockSession by rememberSaveable { mutableIntStateOf(0) }
    var destination by rememberSaveable { mutableStateOf("Home") }
    var route by rememberSaveable { mutableStateOf("main") }
    val currentRoute by rememberUpdatedState(route)
    val currentDestination by rememberUpdatedState(destination)
    val currentBusy by rememberUpdatedState(busy)
    val currentBackupBusy by rememberUpdatedState(backupBusy)
    val currentDownloadState by rememberUpdatedState(downloadState)
    var correctionKind by rememberSaveable { mutableStateOf(CorrectionKind.PhysicalCount.name) }
    var correctionOperation by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var newCartId by rememberSaveable { mutableStateOf("") }
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
        if (route == "cart") {
            val draft = cartDraft
            val isUntouchedNewCart = newCartId == selectedId && draft != null &&
                draft.customer.isBlank() && draft.quantities.isEmpty()
            if (isUntouchedNewCart) {
                model.write({ discardCart(draft.cart.id, draft.cart.editVersion) }, {
                    newCartId = ""; route = "main"; destination = "Carts"
                })
            } else {
                model.flushCart { newCartId = ""; route = "main"; destination = "Carts" }
            }
            return
        }
        route = when (route) { "checkout", "cart-products" -> "cart"; "receipt" -> "sales"; "detail" -> if (product?.archived == true) "archived-products" else "main"; "edit", "fields", "correction", "labels" -> "detail"; "arrival" -> "receive-products"; "template-detail", "template-form" -> "templates"; else -> "main" }
        model.error.value = null
    }
    fun newCart() { model.write({ selectedId = createCart() }, { newCartId = selectedId; model.openCart(selectedId); destination = "Carts"; route = "cart" }) }
    fun addProduct() { model.error.value = null; formSession++; catalogPayload = null; route = "create" }
    fun openStock(health: StockHealth?) {
        stockState.removeState("stock:$stockSession")
        stockSession++
        stockHealth = health?.name
        destination = "Stock"
        model.error.value = null
    }
    BackHandler(route != "main") { if (!busy) back() }
    val selectedCart = state?.carts?.find { it.id == selectedId }
    BikepartTheme(appearance = state?.appearance ?: Appearance.System) {
        val colors = MaterialTheme.colorScheme
        // Material 3 scroll behaviour: the bar keeps its height, gains a tonal surface
        // once content has scrolled under it, and resets when the screen changes.
        // contentOffset accumulates negative consumed.y as content moves under the bar.
        val topBarScroll = TopAppBarDefaults.pinnedScrollBehavior()
        val scrolled by remember { derivedStateOf { topBarScroll.state.contentOffset < 0f } }
        val topBarColor by animateColorAsState(
            if (scrolled) colors.surfaceContainer else colors.surface, label = "topAppBarColor")
        LaunchedEffect(route, destination, selectedId) { topBarScroll.state.contentOffset = 0f }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp && route == "main"
            Scaffold(
                modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
                containerColor = colors.surface,
                topBar = {
                    if (route != "main") CenterAlignedTopAppBar(
                        title = { Text(when (route) {
                            "labels" -> "Product labels"; "scan" -> "Scan Barcode"; "receive-products", "arrival" -> "Receive stock"; "catalog-search" -> "Find catalog details"
                            "templates" -> "Product templates"; "template-detail" -> template?.catalog?.title ?: "Template"
                            "template-form" -> if (templateId == null) "New template" else "Edit template"; "create" -> "New product"
                            "detail" -> "Product details"; "edit" -> "Edit details"
                            "archived-products" -> "Archived products"; "checkout" -> "Cash checkout"; "receipt" -> "Receipt"; "sales" -> "Sales history"
                            "cart" -> selectedCart?.name ?: "Unfinished bill"; "cart-products" -> "Add to cart"
                            "backups" -> "Shop backups"; "correction" -> CorrectionKind.valueOf(correctionKind).label; "finding-lookups" -> "Fitment and locations"; "categories" -> "Categories"; "fields" -> "Catalog fields"; else -> destination
                        }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            ShopBackButton(onClick = { back() }, enabled = !busy)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor),
                    )
                },
                floatingActionButton = {
                    if (route == "main" && state != null && !busy && destination in listOf("Stock", "Carts")) {
                        ExtendedFloatingActionButton(
                            onClick = { if (destination == "Stock") addProduct() else newCart() },
                            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                            text = { Text(if (destination == "Stock") "Add product" else "New sale") },
                            containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                            modifier = Modifier.imePadding().semantics {
                                text = AnnotatedString(if (destination == "Stock") "Add product" else "New sale")
                            })
                    }
                },
                bottomBar = {
                    if (route == "main" && !useRail) NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
                        destinations.forEach { item ->
                            val selected = destination == item.label
                            NavigationBarItem(
                                enabled = !busy,
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
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                    if (useRail) NavigationRail(containerColor = colors.surface) {
                        Spacer(Modifier.height(ShopSpacing.section))
                        destinations.forEach { item ->
                            val selected = destination == item.label
                            NavigationRailItem(selected = selected, enabled = !busy,
                                onClick = { if (item.label == "Stock") openStock(null) else { destination = item.label; model.error.value = null } },
                                icon = { Icon(painterResource(if (selected) item.filled else item.outlined), contentDescription = null) },
                                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationRailItemDefaults.colors(indicatorColor = colors.primaryContainer,
                                    selectedIconColor = colors.onPrimaryContainer, selectedTextColor = colors.primary,
                                    unselectedIconColor = colors.onSurface, unselectedTextColor = colors.onSurface))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)), contentAlignment = Alignment.TopCenter) {
                        Column(
                            if (route == "scan") Modifier.fillMaxSize() else
                                Modifier.widthIn(max = ShopSpacing.formWidth).fillMaxWidth().verticalScroll(scrollState).padding(ShopSpacing.content)
                                    .padding(bottom = if (route == "main" && destination in listOf("Stock", "Carts")) 88.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(ShopSpacing.content),
                        ) {
                            val shop = state
                            if (shop == null) {
                                val openingError = error?.message
                                if (openingError != null) Text(openingError)
                                else Column(Modifier.fillMaxWidth().padding(top = ShopSpacing.section),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                    CircularProgressIndicator()
                                    Text(stringResource(R.string.opening_shop), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                                return@Column
                            }
                            when (route) {
                                "backups" -> {
                                    DriveBackupControls(model, shop.revision)
                                    LocalBackupControls(localCapture, shop.revision, backupBusy, backupError.takeUnless { recoveryExport }, backupSaved && !recoveryExport, model::captureBackup, model::saveBackupCopy)
                                    if (recoveryCapture != null) LocalBackupControls(recoveryCapture, shop.revision, backupBusy, backupError.takeIf { recoveryExport }, backupSaved && recoveryExport,
                                        model::captureBackup, model::saveBackupCopy, recovery = true)
                                }
                                "scan" -> ContextualScanner(shop, ScanPurpose.valueOf(scanPurpose), busy, error, camera,
                                    manual = { route = when (ScanPurpose.valueOf(scanPurpose)) { ScanPurpose.Sale -> "cart-products"; ScanPurpose.Receiving -> "receive-products"; ScanPurpose.Catalog -> "catalog-search" } },
                                    onChange = { model.error.value = null },
                                    add = { id, pieces -> cartDraft?.let { draft -> model.write({ addCartProduct(draft.cart.id, draft.cart.editVersion, id, pieces) }, { model.openCart(draft.cart.id); route = "cart" }) } },
                                    receive = ::receive, open = { selectedId = it; route = "detail" }, copy = { beginCatalog(it, "create") }, cart = cartDraft?.cart,
                                    link = { code, target, revision, done -> model.write({ linkBarcode(code, target, expectedAssignmentRevision = revision) }, done) })
                                "receive-products", "catalog-search" -> {
                                    val forCatalog = route == "catalog-search"
                                    ScanManualPicker(shop, forCatalog, busy, model::readPhoto,
                                        onScan = { scan(if (forCatalog) ScanPurpose.Catalog else ScanPurpose.Receiving, route) },
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
                                    discard = { cartDraft?.let { draft -> model.write({ discardCart(draft.cart.id, draft.cart.editVersion) }, { newCartId = ""; route = "main"; destination = "Carts" }) } })
                                "checkout" -> {
                                    val cart = shop.carts.find { it.id == selectedId }
                                    if (selectedSale != null) SaleReceipt(selectedSale) { model.error.value = null; route = "sales" }
                                    else if (cart != null) CashCheckout(shop, cart, busy, error,
                                        onChange = { model.error.value = null }, edit = { model.openCart(cart.id); route = "cart" },
                                        complete = { cash ->
                                            model.write({ checkout(cart.id, checkoutVersion, cash) }, { route = "receipt" })
                                        })
                                    else Text(stringResource(R.string.cart_unavailable))
                                }
                                "sales" -> SalesHistory(shop) { selectedId = it; model.error.value = null; route = "receipt" }
                                "receipt" -> if (selectedSale != null) SaleReceipt(selectedSale) { model.error.value = null; route = "sales" }
                                    else Text(stringResource(R.string.receipt_unavailable))
                                "cart-products" -> cartDraft?.let { draft ->
                                    CartProductPicker(shop, shop.carts.find { it.id == draft.cart.id } ?: draft.cart, busy, error, model::readPhoto,
                                        onScan = { scan(ScanPurpose.Sale, "cart-products") },
                                        onChange = { model.error.value = null }, add = { productId, pieces ->
                                            model.write({ addCartProduct(draft.cart.id, draft.cart.editVersion, productId, pieces) }, { model.openCart(draft.cart.id); route = "cart" })
                                        })
                                }
                                "create", "edit", "template-form" -> stockState.SaveableStateProvider("form:$formSession:$route:$selectedId:$catalogPayload") { key(route, selectedId, catalogPayload) {
                                    if (route == "create") ShopSecondaryButton(onClick = { scan(ScanPurpose.Catalog, "create") }, enabled = !busy) { Text(stringResource(R.string.manual_scan_catalog)) }
                                    if (route == "create" && catalog == null) ShopSecondaryButton(onClick = { route = "templates" }, enabled = !busy) { Text(stringResource(R.string.btn_choose_template)) }
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
                                                else if (editing != null) {
                                                    editProduct(editing.id, input.catalog.title, input.catalog.categoryId, input.catalog.description, input.catalog.attributes, input.categoryMappings, input.catalog.modelIds, input.locationIds, photo)
                                                    setSellingPrice(editing.id, input.priceRupees)
                                                }
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
                                "labels" -> if (product != null) LabelPreview(shop, product, busy, error, usbAccess = labelUsb,
                                    onChange = { model.error.value = null }, save = { media, copies -> model.write({ setLabelPreferences(media, copies) }) })
                                "detail" -> if (product != null) {
                                    ProductDetails(shop, product, onEdit = { route = "edit" }, onManage = { route = "fields" }, loadPhoto = model::readPhoto, busy = busy, onCopy = { beginCatalog(shop.copyCatalog(product.id), "create") }, onTemplate = { beginCatalog(shop.copyCatalog(product.id), "template-form") }, onCorrection = { kind ->
                                    model.error.value = null; correctionKind = kind.name; correctionOperation = java.util.UUID.randomUUID().toString(); route = "correction"
                                }, onArchive = { model.write({ archiveProduct(product.id) }) }, archiveError = error?.takeIf { it.field == null }?.message,
                                    onReceive = { receive(product.id) }, onLabels = { model.error.value = null; route = "labels" })
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
                                    "Home" -> HomeScreen(shop, onAdd = { addProduct() }, onStock = ::openStock, onNewSale = ::newCart,
                                        onCarts = { destination = "Carts" }, onReceive = { route = "receive-products" },
                                        onSales = { route = "sales" }, onSale = { selectedId = it; model.error.value = null; route = "receipt" },
                                        busy = busy)
                                    "Stock" -> stockState.SaveableStateProvider("stock:$stockSession") {
                                        StockScreen(shop, onAdd = { addProduct() }, onOpen = { selectedId = it; route = "detail" }, loadPhoto = model::readPhoto, initialHealth = stockHealth, busy = busy,
                                            onScan = { scan(ScanPurpose.Catalog, "main") },
                                            onProductAction = { item, action ->
                                                selectedId = item.id; model.error.value = null
                                                when (action) {
                                                    "edit" -> route = "edit"
                                                    "copy" -> beginCatalog(shop.copyCatalog(item.id), "create")
                                                    "labels" -> route = "labels"
                                                    "receive" -> receive(item.id)
                                                }
                                            })
                                    }
                                    "Carts" -> CartList(shop, busy, ::newCart, onOpen = { newCartId = ""; selectedId = it; model.openCart(it); route = "cart" })
                                    "More" -> {
                                        MoreScreen(shop, shop.appearance, busy,
                                            onAppearance = { model.write({ setAppearance(it) }) },
                                            onCategories = { route = "categories" },
                                            onFindingLookups = { route = "finding-lookups" },
                                            onArchived = { route = "archived-products" },
                                            onSales = { route = "sales" },
                                            onTemplates = { route = "templates" }, onBackups = { route = "backups" },
                                            updates = { UpdateControls(updateService.installed, updateState, downloadState, {
                                                if (updateJob?.isActive != true && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                                    updateJob = updateScope.launch { updateService.check() }
                                                }
                                            }, { installError = null; updateService.startDownload() }, updateService::cancelDownload,
                                            installError = installError, installing = installing, installNow = {
                                                val ready = downloadState as? DownloadState.Ready
                                                if (ready != null && !busy && !installing) {
                                                    installing = true
                                                    installError = null
                                                    updateScope.launch {
                                                        try {
                                                            when (installService.install(ready.apk, ready.release) {
                                                                currentRoute == "main" && currentDestination == "More" && !currentBusy && !currentBackupBusy &&
                                                                    lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && currentDownloadState == ready
                                                            }) {
                                                                InstallResult.InvalidApk -> {
                                                                    updateService.discardInvalidDownload()
                                                                    installError = invalidApkMsg
                                                                }
                                                                InstallResult.PermissionRequired -> installError = permissionNeededMsg
                                                                InstallResult.Failed -> installError = installFailedMsg
                                                                InstallResult.Success, InstallResult.Cancelled -> Unit
                                                            }
                                                        } finally { installing = false }
                                                    }
                                                }
                                            },
                                            installLater = {
                                                // Retain completed file in ready state, keeping it accessible in More
                                            }) })
                                    }
                                }
                            }
                            if (error != null && route != "scan" && route != "arrival" && route != "receipt" && route != "checkout" && route != "cart" && route != "cart-products" && !(route == "detail" && (error?.field == ProductField.LowStockThreshold || error?.field == null)) && route != "correction" && route != "create" && route != "edit" && route != "template-form" &&
                                !(route == "fields" && error?.field in listOf(com.nadim.bikepart.shop.ProductField.DefinitionName, com.nadim.bikepart.shop.ProductField.DefinitionType))) FormError(requireNotNull(error).message)
                        }
                    }
                }
            }

            var reminderRevision by remember { mutableIntStateOf(0) }
            val availableUpdate = (updateState as? UpdateState.Available)?.release
            val isIdle = route == "main" && destination == "Home" && !busy && !backupBusy && error == null
            if (isIdle && availableUpdate != null &&
                reminderRevision >= 0 && reminderService.shouldShowReminder(availableUpdate) && downloadState !is DownloadState.Downloading &&
                downloadState !is DownloadState.Verifying) {
                UpdateReminderDialog(
                    release = availableUpdate,
                    onUpdate = {
                        reminderService.dismissReminder(availableUpdate)
                        reminderRevision++
                        destination = "More"
                        updateService.startDownload()
                    },
                    onDismiss = {
                        reminderService.dismissReminder(availableUpdate)
                        reminderRevision++
                    }
                )
            }
        }
    }
}

/** Configuration changes recreate the activity but must not stop an active transfer. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
