package com.nadim.bikepart.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import com.nadim.bikepart.ui.theme.shopIconVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
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
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.tokenized.navigation.TabBar
import com.microsoft.fluentui.tokenized.navigation.TabData
import com.microsoft.fluentui.tokenized.navigation.SideRail
import com.microsoft.fluentui.tokenized.AppBar
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.microsoft.fluentui.theme.token.FluentIcon
import com.microsoft.fluentui.theme.token.controlTokens.AppBarSize
import com.microsoft.fluentui.tokenized.SearchBar

private data class MainDestination(val screen: Screen, val label: String, val outlined: Int, val filled: Int)
private val destinations = listOf(
    MainDestination(Screen.Home, "Home", R.drawable.ic_home, R.drawable.ic_home_filled),
    MainDestination(Screen.Stock, "Stock", R.drawable.ic_stock, R.drawable.ic_stock_filled),
    MainDestination(Screen.Carts, "Carts", R.drawable.ic_carts, R.drawable.ic_carts_filled),
    MainDestination(Screen.More, "More", R.drawable.ic_more, R.drawable.ic_more_filled),
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
                if (context.findActivity()?.isChangingConfigurations != true) updateService.cancelDownload()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); updateJob?.cancel() }
    }
    val stockState = rememberSaveableStateHolder()
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val customization by model.themePreferences.state.collectAsStateWithLifecycle()
    val themeConfiguration = remember(customization.brand, customization.accent) {
        com.nadim.bikepart.ui.theme.ShopThemeConfiguration(aliasTokens = customization.brand.aliasTokens,
            style = if (customization.accent) com.microsoft.fluentui.theme.token.FluentStyle.Brand else com.microsoft.fluentui.theme.token.FluentStyle.Neutral)
    }
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
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var stockHealth by rememberSaveable { mutableStateOf<String?>(null) }
    var stockSession by rememberSaveable { mutableIntStateOf(0) }
    var destination by rememberSaveable { mutableStateOf<Screen>(Screen.Home) }
    var route by rememberSaveable { mutableStateOf<Screen>(Screen.Home) }
    fun selectTab(screen: Screen) {
        model.error.value = null
        destination = screen
        route = screen
        backStack.clear()
        backStack.add(screen)
    }
    fun navigateTo(targetRoute: Screen) {
        model.error.value = null
        route = targetRoute
        if (backStack.lastOrNull() != targetRoute) {
            backStack.add(targetRoute)
        }
    }
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
    var scanReturn by rememberSaveable { mutableStateOf<Screen>(Screen.Create) }
    var arrivalOperation by rememberSaveable { mutableStateOf("") }
    fun scan(purpose: ScanPurpose, returnRoute: Screen) { scanPurpose = purpose.name; scanReturn = returnRoute; navigateTo(Screen.Scan) }
    fun receive(id: String) { selectedId = id; arrivalOperation = java.util.UUID.randomUUID().toString(); navigateTo(Screen.Arrival) }
    var checkoutVersion by rememberSaveable { mutableLongStateOf(0L) }
    val selectedSale = state?.sales?.find { it.cartId == selectedId }
    LaunchedEffect(state?.revision, route, selectedId) {
        if (route in listOf(Screen.Cart, Screen.Checkout) && selectedSale != null) { navigateTo(Screen.Receipt) }
    }
    LaunchedEffect(state != null, route) {
        if (state != null && (route in listOf(Screen.Cart, Screen.CartProducts) || route is Screen.Scan && scanPurpose == ScanPurpose.Sale.name) && cartDraft == null) model.openCart(selectedId)
    }
    var catalogPayload by rememberSaveable { mutableStateOf<String?>(null) }
    var formSession by rememberSaveable { mutableIntStateOf(0) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = catalogPayload?.let { decodeCatalog(it) }
    val template = state?.templates?.find { it.id == templateId }
    fun beginCatalog(draft: CatalogDraft, target: Screen, id: String? = null) { formSession++; catalogPayload = draft.encode(); templateId = id; navigateTo(target) }
    val product = state?.products?.find { it.id == selectedId }
    val fluentStyle = if (customization.accent) FluentStyle.Brand else FluentStyle.Neutral

    fun back() {
        if (route is Screen.Scan) { navigateTo(scanReturn); return }
        if (route is Screen.Cart) {
            val draft = cartDraft
            val isUntouchedNewCart = newCartId == selectedId && draft != null &&
                draft.customer.isBlank() && draft.quantities.isEmpty()
            if (isUntouchedNewCart) {
                model.write({ discardCart(draft.cart.id, draft.cart.editVersion) }, {
                    newCartId = ""; selectTab(Screen.Carts)
                })
            } else {
                model.flushCart { newCartId = ""; selectTab(Screen.Carts) }
            }
            return
        }
        val target = when (route) {
            Screen.Checkout, Screen.CartProducts -> Screen.Cart
            Screen.Receipt -> Screen.Sales
            Screen.Detail -> if (product?.archived == true) Screen.ArchivedProducts else destination
            Screen.Edit, Screen.Fields, Screen.Correction, Screen.Labels -> Screen.Detail
            Screen.Arrival -> Screen.ReceiveProducts
            Screen.TemplateDetail, Screen.TemplateForm -> Screen.Templates
            else -> destination
        }
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        route = backStack.lastOrNull() ?: target
    }
    fun newCart() { model.write({ selectedId = createCart() }, { newCartId = selectedId; model.openCart(selectedId); destination = Screen.Carts; navigateTo(Screen.Cart) }) }
    fun addProduct() { catalogPayload = null; formSession++; navigateTo(Screen.Create) }
    fun openStock(health: StockHealth?) {
        stockState.removeState("stock:$stockSession")
        stockSession++
        stockHealth = health?.name
        selectTab(Screen.Stock)
    }
    BackHandler(route !in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More)) { if (!busy) back() }
    val selectedCart = state?.carts?.find { it.id == selectedId }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val qrVector = shopIconVector(R.drawable.ic_qr_code_scanner)
    val searchBarContent: @Composable RowScope.() -> Unit = {
        SearchBar(
            onValueChange = { query, _ ->
                searchQuery = query
                if (query.isNotBlank()) {
                   // show products titles
                }
            },
            style = fluentStyle,
            searchHint = "Search products",
            rightAccessoryIcon = FluentIcon(
                light = qrVector,
                dark = qrVector,
                contentDescription = "Scan product",
                onClick = { }
            ),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        )
    }

    BikepartTheme(appearance = state?.appearance ?: Appearance.System, configuration = themeConfiguration) {
        val colors = MaterialTheme.colorScheme
        val appBarColors = com.nadim.bikepart.ui.theme.shopAppBarColors()
        com.nadim.bikepart.ui.theme.ShopSystemBarAppearance(
            statusBackground = if (route in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More)) colors.surface else appBarColors.background,
            navigationBackground = colors.surface)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp && route in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More)
            Scaffold(
                containerColor = colors.surface,
                topBar = {
                    val title = when (route) {
                        Screen.Labels -> "Product labels"; Screen.Scan -> "Scan Barcode"; Screen.ReceiveProducts, Screen.Arrival -> "Receive stock"; Screen.CatalogSearch -> "Find catalog details"
                        Screen.Templates -> "Product templates"; Screen.TemplateDetail -> template?.catalog?.title ?: "Template"
                        Screen.TemplateForm -> if (templateId == null) "New template" else "Edit template"; Screen.Create -> "New product"
                        Screen.Detail -> "Product details"; Screen.Edit -> "Edit details"
                        Screen.ArchivedProducts -> "Archived products"; Screen.Checkout -> "Cash checkout"; Screen.Receipt -> "Receipt"; Screen.Sales -> "Sales history"
                        Screen.Cart -> selectedCart?.name ?: "Unfinished bill"; Screen.CartProducts -> "Add to cart"
                        Screen.Backups -> "Shop backups"; Screen.Correction -> CorrectionKind.valueOf(correctionKind).label; Screen.FindingLookups -> "Fitment and locations"; Screen.Categories -> "Categories"; Screen.Fields -> "Catalog fields"; else -> destinations.find { it.screen == destination }?.label ?: "Home"
                    }
                    val canGoBack = route !in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More)
                    val searchMode = route in listOf(Screen.Home)
                    Box(Modifier
                        .fillMaxWidth()
                        .background(appBarColors.background)
                        .statusBarsPadding()) {
                        AppBar(
                            title = title,
                            appBarSize = AppBarSize.Medium,
                            style = fluentStyle,
                            searchMode = searchMode,
                            searchBar = searchBarContent,
                            centerAlignAppBar = true,
                            logo = if (canGoBack) {
                                { ShopBackButton(onClick = { back() }, enabled = !busy) }
                            } else null,
                        )
                    }
                },
                floatingActionButton = {
                    if (route in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More) && state != null && !busy && destination in listOf(Screen.Stock, Screen.Carts)) {
                        ExtendedFloatingActionButton(
                            onClick = { if (destination == Screen.Stock) addProduct() else newCart() },
                            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                            text = { Text(if (destination == Screen.Stock) "Add product" else "New sale") },
                            containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                            modifier = Modifier
                                .imePadding()
                                .semantics {
                                    text =
                                        AnnotatedString(if (destination == Screen.Stock) "Add product" else "New sale")
                                })
                    }
                },
                bottomBar = {
                    if (route in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More) && !useRail) {
                        val tabs = destinations.map { item ->
                            TabData(
                                title = item.label,
                                icon = shopIconVector(item.outlined),
                                selectedIcon = shopIconVector(item.filled),
                                onClick = { if (!busy) { if (item.screen == Screen.Stock) openStock(null) else selectTab(item.screen) } },
                            )
                        }
                        TabBar(
                            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                            tabDataList = tabs,
                            selectedIndex = destinations.indexOfFirst { it.screen == destination }.coerceAtLeast(0),
                            showIndicator = false,
                        )
                    }
                },
            ) { padding ->
                val scrollState = key(route, destination, selectedId) { rememberScrollState() }
                Row(Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()) {
                    if (useRail) {
                        val tabs = destinations.map { item ->
                            TabData(
                                title = item.label,
                                icon = shopIconVector(item.outlined),
                                selectedIcon = shopIconVector(item.filled),
                                onClick = { if (!busy) { if (item.screen == Screen.Stock) openStock(null) else selectTab(item.screen) } },
                            )
                        }
                        SideRail(
                            topTabDataList = tabs,
                            topTabSelectedIndex = destinations.indexOfFirst { it.screen == destination }.coerceAtLeast(0),
                            showIconText = true,
                        )
                    }
                    Box(Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)), contentAlignment = Alignment.TopCenter) {
                        val isScanActive = route is Screen.Scan || backStack.lastOrNull() is Screen.Scan
                        Column(
                            if (route is Screen.Scan) Modifier.fillMaxSize() else
                                Modifier
                                    .widthIn(max = ShopSpacing.formWidth)
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(ShopSpacing.content)
                                    .padding(
                                        bottom = if (route in listOf(
                                                Screen.Home,
                                                Screen.Stock,
                                                Screen.Carts,
                                                Screen.More
                                            ) && destination in listOf(Screen.Stock, Screen.Carts)
                                        ) 88.dp else 0.dp
                                    ),
                            verticalArrangement = Arrangement.spacedBy(ShopSpacing.content),
                        ) {
                            val shop = state
                            if (shop == null) {
                                val openingError = error?.message
                                if (openingError != null) Text(openingError)
                                else Column(Modifier
                                    .fillMaxWidth()
                                    .padding(top = ShopSpacing.section),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                    CircularProgressIndicator()
                                    Text(stringResource(R.string.opening_shop), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                                return@Column
                            }
                            NavDisplay(
                                backStack = backStack,
                                onBack = { if (backStack.size > 1) back() },
                                entryProvider = { key ->
                                    when (key) {
                                        is Screen.Home -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                HomeScreen(shop, onAdd = { addProduct() }, onStock = ::openStock, onNewSale = ::newCart,
                                                    onCarts = { selectTab(Screen.Carts) }, onReceive = { navigateTo(Screen.ReceiveProducts) },
                                                    onSales = { navigateTo(Screen.Sales) }, onSale = { selectedId = it; model.error.value = null; navigateTo(Screen.Receipt) },                                                    busy = busy)
                                            }
                                        }
                                        is Screen.Stock -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                stockState.SaveableStateProvider("stock:$stockSession") {
                                                    StockScreen(shop, onAdd = { addProduct() }, onOpen = { selectedId = it; navigateTo(Screen.Detail) }, loadPhoto = model::readPhoto, initialHealth = stockHealth, busy = busy,
                                                        onScan = { scan(ScanPurpose.Catalog, Screen.Home) },
                                                        onProductAction = { item, action ->
                                                            selectedId = item.id; model.error.value = null
                                                            when (action) {
                                                                "edit" -> navigateTo(Screen.Edit)
                                                                "copy" -> beginCatalog(shop.copyCatalog(item.id), Screen.Create)
                                                                "labels" -> navigateTo(Screen.Labels)
                                                                "receive" -> receive(item.id)
                                                            }
                                                        })
                                                }
                                            }
                                        }
                                        is Screen.Carts -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                CartList(shop, busy, ::newCart, onOpen = { newCartId = ""; selectedId = it; model.openCart(it); navigateTo(Screen.Cart) })
                                            }
                                        }
                                        is Screen.More -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                MoreScreen(shop, shop.appearance, busy,
                                                    onAppearance = { model.write({ setAppearance(it) }) },
                                                    customization = customization, onBrand = model.themePreferences::setBrand, onAccent = model.themePreferences::setAccent,
                                                    onCategories = { navigateTo(Screen.Categories) },
                                                    onFindingLookups = { navigateTo(Screen.FindingLookups) },
                                                    onArchived = { navigateTo(Screen.ArchivedProducts) },
                                                    onSales = { navigateTo(Screen.Sales) },
                                                    onTemplates = { navigateTo(Screen.Templates) }, onBackups = { navigateTo(Screen.Backups) },
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
                                                                        currentRoute in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More) && currentDestination == Screen.More && !currentBusy && !currentBackupBusy &&
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
                                        is Screen.Backups -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                DriveBackupControls(model, shop.revision)
                                                LocalBackupControls(localCapture, shop.revision, backupBusy, backupError.takeUnless { recoveryExport }, backupSaved && !recoveryExport, model::captureBackup, model::saveBackupCopy)
                                                if (recoveryCapture != null) LocalBackupControls(recoveryCapture, shop.revision, backupBusy, backupError.takeIf { recoveryExport }, backupSaved && recoveryExport,
                                                    model::captureBackup, model::saveBackupCopy, recovery = true)
                                            }
                                        }
                                        is Screen.Scan -> NavEntry(key) {
                                            ContextualScanner(shop, ScanPurpose.valueOf(scanPurpose), busy, error, camera,
                                                manual = { navigateTo(when (ScanPurpose.valueOf(scanPurpose)) { ScanPurpose.Sale -> Screen.CartProducts; ScanPurpose.Receiving -> Screen.ReceiveProducts; ScanPurpose.Catalog -> Screen.CatalogSearch; ScanPurpose.ProductDetail -> Screen.CatalogSearch }) },
                                                onChange = { model.error.value = null },
                                                add = { id, pieces -> cartDraft?.let { draft -> model.write({ addCartProduct(draft.cart.id, draft.cart.editVersion, id, pieces) }, { model.openCart(draft.cart.id); navigateTo(Screen.Cart) }) } },
                                                receive = ::receive, open = { selectedId = it; navigateTo(Screen.Detail) }, copy = { beginCatalog(it, Screen.Create) }, cart = cartDraft?.cart,
                                                link = { code, target, revision, done -> model.write({ linkBarcode(code, target, expectedAssignmentRevision = revision) }, done) })
                                        }
                                        is Screen.ReceiveProducts, is Screen.CatalogSearch -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                val forCatalog = key is Screen.CatalogSearch
                                                ScanManualPicker(shop, forCatalog, busy, model::readPhoto,
                                                    onScan = { scan(if (forCatalog) ScanPurpose.Catalog else ScanPurpose.Receiving, key) },
                                                    select = { if (forCatalog) { selectedId = it; navigateTo(Screen.Detail) } else receive(it) },
                                                    template = { beginCatalog(shop.prefillTemplate(it), Screen.Create) }, create = { navigateTo(Screen.Create) })
                                            }
                                        }
                                        is Screen.Arrival -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (product != null && !product.archived) key(arrivalOperation) {
                                                    StockArrivalForm(product, busy, error, onChange = { model.error.value = null },
                                                        save = { pieces, cost -> model.write({ receiveStock(arrivalOperation, product.id, pieces, cost) }, { navigateTo(Screen.Detail) }) })
                                                }
                                            }
                                        }
                                        is Screen.Cart -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                CartEditor(shop, cartDraft, busy, error,
                                                    edit = model::editCart, pause = ::back, add = { navigateTo(Screen.CartProducts) },
                                                    retry = { model.flushCart() }, reload = model::reloadCart,
                                                    checkout = { model.flushCart { checkoutVersion = requireNotNull(model.cartDraft.value).cart.editVersion; model.error.value = null; navigateTo(Screen.Checkout) } },
                                                    remove = { productId -> cartDraft?.let { draft ->
                                                        model.write({ saveCart(draft.cart.id, draft.cart.editVersion, draft.customer, draft.quantities - productId) }, { model.openCart(draft.cart.id) })
                                                    } },
                                                    discard = { cartDraft?.let { draft -> model.write({ discardCart(draft.cart.id, draft.cart.editVersion) }, { newCartId = ""; selectTab(Screen.Carts) }) } })
                                            }
                                        }
                                        is Screen.Checkout -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                val cart = shop.carts.find { it.id == selectedId }
                                                if (selectedSale != null) SaleReceipt(selectedSale) { model.error.value = null; navigateTo(Screen.Sales) }
                                                else if (cart != null) CashCheckout(shop, cart, busy, error,
                                                    onChange = { model.error.value = null }, edit = { model.openCart(cart.id); navigateTo(Screen.Cart) },
                                                    complete = { cash ->
                                                        model.write({ checkout(cart.id, checkoutVersion, cash) }, { navigateTo(Screen.Receipt) })
                                                    })
                                                else Text(stringResource(R.string.cart_unavailable))
                                            }
                                        }
                                        is Screen.Sales -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                SalesHistory(shop) { selectedId = it; model.error.value = null; navigateTo(Screen.Receipt) }
                                            }
                                        }
                                        is Screen.Receipt -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (selectedSale != null) SaleReceipt(selectedSale) { model.error.value = null; navigateTo(Screen.Sales) }
                                                else Text(stringResource(R.string.receipt_unavailable))
                                            }
                                        }
                                        is Screen.CartProducts -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                cartDraft?.let { draft ->
                                                    CartProductPicker(shop, shop.carts.find { it.id == draft.cart.id } ?: draft.cart, busy, error, model::readPhoto,
                                                        onScan = { scan(ScanPurpose.Sale, Screen.CartProducts) },
                                                        onChange = { model.error.value = null }, add = { productId, pieces ->
                                                            model.write({ addCartProduct(draft.cart.id, draft.cart.editVersion, productId, pieces) }, { model.openCart(draft.cart.id); navigateTo(Screen.Cart) })
                                                        })
                                                }
                                            }
                                        }
                                        is Screen.Create, is Screen.Edit, is Screen.TemplateForm -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                stockState.SaveableStateProvider("form:$formSession:$route:$selectedId:$catalogPayload") { key(route, selectedId, catalogPayload) {
//                                                    if (route == Screen.Create) ShopSecondaryButton(onClick = { scan(ScanPurpose.Catalog, Screen.Create) }, enabled = !busy) { Text(stringResource(R.string.manual_scan_catalog)) }
//                                                    if (route == Screen.Create && catalog == null) ShopSecondaryButton(onClick = { navigateTo(Screen.Templates) }, enabled = !busy) { Text(stringResource(R.string.btn_choose_template)) }
                                                    ProductForm(shop, if (route == Screen.Edit) product else null, busy, error,
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
                                                        initialCatalog = if (route == Screen.Edit) null else catalog,
                                                        templateMode = route == Screen.TemplateForm,
                                                        save = { input ->
                                                            val editing = if (route == Screen.Edit) product else null
                                                            val photo = input.photoUri?.let { uri -> com.nadim.bikepart.shop.PhotoChange.Import {
                                                                resolver.openInputStream(uri.toUri()) ?: throw com.nadim.bikepart.shop.ShopValidation(com.nadim.bikepart.shop.ProductField.Photo, "Photo unavailable. Choose it again or remove it.")
                                                            } } ?: if (input.photoRemoved) com.nadim.bikepart.shop.PhotoChange.Remove else com.nadim.bikepart.shop.PhotoChange.Keep
                                                            model.write({
                                                                if (route == Screen.TemplateForm) templateId = saveTemplate(input.catalog, templateId, photo)
                                                                else if (editing != null) {
                                                                    editProduct(editing.id, input.catalog.title, input.catalog.categoryId, input.catalog.description, input.catalog.attributes, input.categoryMappings, input.catalog.modelIds, input.locationIds, photo)
                                                                    setSellingPrice(editing.id, input.priceRupees)
                                                                }
                                                                else selectedId = createFromCatalog(input.catalog, input.openingPieces, input.priceRupees, input.allowSimilar, input.locationIds, photo)
                                                            }, { navigateTo(if (route == Screen.TemplateForm) Screen.TemplateDetail else Screen.Detail) })
                                                        },
                                                    )
                                                } }
                                            }
                                        }
                                        is Screen.Templates -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                TemplateList(shop, busy,
                                                    onCreate = { beginCatalog(CatalogDraft(), Screen.TemplateForm) },
                                                    onOpen = { templateId = it; navigateTo(Screen.TemplateDetail) },
                                                    onUse = { beginCatalog(shop.prefillTemplate(it), Screen.Create) })
                                            }
                                        }
                                        is Screen.TemplateDetail -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (template != null) TemplateDetails(shop, template, busy, model::readPhoto,
                                                    onEdit = { beginCatalog(template.catalog, Screen.TemplateForm, template.id) },
                                                    onUse = { beginCatalog(shop.prefillTemplate(template.id), Screen.Create) },
                                                    onRetire = { model.write({ retireTemplate(template.id) }) })
                                            }
                                        }
                                        is Screen.Correction -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (product != null) key(correctionOperation) {
                                                    StockCorrectionForm(product, CorrectionKind.valueOf(correctionKind), busy, error,
                                                        onChange = { model.error.value = null },
                                                        save = { quantity, reason -> model.write({ correctStock(correctionOperation, product.id, CorrectionKind.valueOf(correctionKind), quantity, reason) }, { navigateTo(Screen.Detail) }) })
                                                }
                                            }
                                        }
                                        is Screen.ArchivedProducts -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                ArchivedProducts(shop, model::readPhoto) { selectedId = it; navigateTo(Screen.Detail) }
                                            }
                                        }
                                        is Screen.Labels -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (product != null) LabelPreview(shop, product, busy, error, usbAccess = labelUsb,
                                                    onChange = { model.error.value = null }, save = { media, copies -> model.write({ setLabelPreferences(media, copies) }) })
                                            }
                                        }
                                        is Screen.Detail -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (product != null) {
                                                    ProductDetails(shop, product, onEdit = { navigateTo(Screen.Edit) }, onManage = { navigateTo(Screen.Fields) }, loadPhoto = model::readPhoto, busy = busy, onCopy = { beginCatalog(shop.copyCatalog(product.id), Screen.Create) }, onTemplate = { beginCatalog(shop.copyCatalog(product.id), Screen.TemplateForm) }, onCorrection = { kind ->
                                                        model.error.value = null; correctionKind = kind.name; correctionOperation = java.util.UUID.randomUUID().toString(); navigateTo(Screen.Correction)
                                                    }, onArchive = { model.write({ archiveProduct(product.id) }) }, archiveError = error?.takeIf { it.field == null }?.message,
                                                        onReceive = { receive(product.id) }, onLabels = { navigateTo(Screen.Labels) })
                                                    if (!product.archived) ThresholdForm(product, busy, error, onChange = { model.error.value = null },
                                                        save = { pieces -> model.write({ setLowStockThreshold(product.id, pieces) }) })
                                                    SaleStockHistory(shop, product.id)
                                                    ArrivalHistory(shop.arrivals.filter { it.productId == product.id })
                                                }
                                            }
                                        }
                                        is Screen.Fields -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                if (product != null) CatalogManagement(shop, product, busy, error, { model.error.value = null }, CatalogActions(
                                                    custom = { input, done -> model.write({ createCustomAttribute(product.id, input.name, input.type, input.required, input.unit) }, done) },
                                                    promote = { id, name -> model.write({ promoteCustomAttribute(id, name) }) },
                                                    retire = { id -> model.write({ retireAttribute(id) }) },
                                                    retireOption = { id -> model.write({ retireOption(id) }) },
                                                    required = { id, required -> model.write({ setAttributeRequired(id, required) }) },
                                                    replace = { id, input, done -> model.write({ replaceAttribute(id, input.name, input.type, input.required, input.unit) }, done) },
                                                ))
                                            }
                                        }
                                        is Screen.FindingLookups -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                FindingLookupSettings(shop, busy,
                                                    retireMake = { model.write({ retireMake(it) }) },
                                                    retireModel = { model.write({ retireModel(it) }) },
                                                    retireLocation = { model.write({ retireLocation(it) }) })
                                            }
                                        }
                                        is Screen.Categories -> NavEntry(key) {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                CategorySettings(shop, busy, onRename = { id, name -> model.write({ renameCategory(id, name) }) })
                                            }
                                        }
                                    }
                                }
                            )
                            if (error != null && route !in listOf(Screen.Scan, Screen.Arrival, Screen.Receipt, Screen.Checkout, Screen.Cart, Screen.CartProducts, Screen.Correction, Screen.Create, Screen.Edit, Screen.TemplateForm) &&
                                !(route == Screen.Detail && (error?.field == ProductField.LowStockThreshold || error?.field == null)) &&
                                !(route == Screen.Fields && error?.field in listOf(com.nadim.bikepart.shop.ProductField.DefinitionName, com.nadim.bikepart.shop.ProductField.DefinitionType))) {
                                FormError(requireNotNull(error).message)
                            }
                        }
                    }
                }
            }

            var reminderRevision by remember { mutableIntStateOf(0) }
            val availableUpdate = (updateState as? UpdateState.Available)?.release
            val isIdle = route in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More) && destination == Screen.Home && !busy && !backupBusy && error == null
            if (isIdle && availableUpdate != null &&
                reminderRevision >= 0 && reminderService.shouldShowReminder(availableUpdate) && downloadState !is DownloadState.Downloading &&
                downloadState !is DownloadState.Verifying) {
                UpdateReminderDialog(
                    release = availableUpdate,
                    onUpdate = {
                        reminderService.dismissReminder(availableUpdate)
                        reminderRevision++
                        selectTab(Screen.More)
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
