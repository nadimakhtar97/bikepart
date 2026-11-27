package com.nadim.bikepart.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.microsoft.fluentui.compose.Scaffold
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.Background1
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralBackgroundColorTokens.CanvasBackground
import com.microsoft.fluentui.theme.token.FluentAliasTokens.NeutralForegroundColorTokens.Foreground2
import com.microsoft.fluentui.tokenized.navigation.SideRail
import com.microsoft.fluentui.tokenized.navigation.TabBar
import com.microsoft.fluentui.tokenized.navigation.TabData
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopViewModel
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.shop.CatalogDraft
import com.nadim.bikepart.shop.CorrectionKind
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.StockHealth
import com.nadim.bikepart.shop.copyCatalog
import com.nadim.bikepart.shop.decodeCatalog
import com.nadim.bikepart.shop.encode
import com.nadim.bikepart.shop.prefillTemplate
import com.nadim.bikepart.ui.theme.BikepartTheme
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.nadim.bikepart.ui.theme.shopIconVector
import com.nadim.bikepart.updates.AppInstaller
import com.nadim.bikepart.updates.AppUpdater
import com.nadim.bikepart.updates.DownloadState
import com.nadim.bikepart.updates.InstallResult
import com.nadim.bikepart.updates.UpdateReminders
import com.nadim.bikepart.updates.UpdateState
import com.nadim.bikepart.updates.UpdateViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


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
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { stack -> stack.toList() },
            restore = { screens -> mutableStateListOf<Screen>().apply { addAll(screens) } },
        ),
    ) { mutableStateListOf<Screen>(Screen.Home) }
    var stockQuery by rememberSaveable { mutableStateOf("") }
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
    var selectedCategoryId by rememberSaveable { mutableStateOf("") }
    val catalog = catalogPayload?.let { decodeCatalog(it) }
    val template = state?.templates?.find { it.id == templateId }
    fun beginCatalog(draft: CatalogDraft, target: Screen, id: String? = null) { formSession++; catalogPayload = draft.encode(); templateId = id; navigateTo(target) }
    val product = state?.products?.find { it.id == selectedId }

    fun back() {
        if (route is Screen.Scan) {
            if (backStack.getOrNull(backStack.lastIndex - 1) == scanReturn) {
                backStack.removeLastOrNull()
                route = scanReturn
                model.error.value = null
            } else {
                navigateTo(scanReturn)
            }
            return
        }
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
            is Screen.CategoryDetail -> Screen.Categories
            else -> destination
        }
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
        model.error.value = null
        route = backStack.lastOrNull() ?: target
    }
    fun newCart() { model.write({ selectedId = createCart() }, { newCartId = selectedId; model.openCart(selectedId); destination = Screen.Carts; navigateTo(Screen.Cart) }) }
    fun addProduct() { catalogPayload = null; formSession++; navigateTo(Screen.Create) }
    fun openStock(health: StockHealth?, query: String = "") {
        stockState.removeState("stock:$stockSession")
        stockSession++
        stockHealth = health?.name
        stockQuery = query
        selectTab(Screen.Stock)
    }
    BackHandler(route !in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More)) { if (!busy) back() }
    val selectedCart = state?.carts?.find { it.id == selectedId }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchBarContent: @Composable RowScope.() -> Unit = {
        ShopSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Search products",
            enabled = !busy,
            onSubmit = {
                val query = searchQuery.trim()
                if (query.isNotEmpty()) openStock(null, query)
            },
            scanLabel = stringResource(R.string.search_scan_stock),
            onScan = { scan(ScanPurpose.Catalog, Screen.Home) },
        )
    }

    BikepartTheme(appearance = state?.appearance ?: Appearance.System, configuration = themeConfiguration) {
        val themeMode = FluentTheme.themeMode
        val surface = FluentTheme.aliasTokens.neutralBackgroundColor[Background1].value(themeMode)
        val canvas =
            FluentTheme.aliasTokens.neutralBackgroundColor[CanvasBackground].value(themeMode)
        val appBarColors = com.nadim.bikepart.ui.theme.shopAppBarColors()
        com.nadim.bikepart.ui.theme.ShopSystemBarAppearance(
            statusBackground = if (route in listOf(
                    Screen.Home,
                    Screen.Stock,
                    Screen.Carts,
                    Screen.More
                )
            ) surface else appBarColors.background,
            navigationBackground = surface
        )
        BoxWithConstraints(Modifier
            .fillMaxSize()
            .background(canvas)) {
            val useRail = maxWidth >= 600.dp && route in listOf(Screen.Home, Screen.Stock, Screen.Carts, Screen.More)
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            modifier = Modifier
                                .background(surface)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                            tabDataList = tabs,
                            selectedIndex = destinations.indexOfFirst { it.screen == destination }.coerceAtLeast(0),
                            showIndicator = false,
                        )
                    }
                },
            ) { padding ->
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
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ErrorSnackbarHost(route) {
                            ErrorSnackbar(error?.message, blocking = state == null, attempt = error)
                            val shop = state
                            if (shop == null) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    topBar = { ShopRouteAppBar(title = "Home", showBack = false) },
                                ) { loadingPadding ->
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(loadingPadding)
                                            .consumeWindowInsets(loadingPadding),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (error == null) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(
                                                    ShopSpacing.content
                                                ),
                                            ) {
                                                CircularProgressIndicator()
                                                Text(
                                                    stringResource(R.string.opening_shop),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = FluentTheme.aliasTokens.neutralForegroundColor[Foreground2].value(
                                                        FluentTheme.themeMode
                                                    ),
                                                    modifier = Modifier.semantics {
                                                        liveRegion = LiveRegionMode.Polite
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                return@ErrorSnackbarHost
                            }
                            val formContext = ProductFormRouteContext(
                                shop = shop,
                                product = product,
                                busy = busy,
                                error = error,
                                stateHolder = stockState,
                                stateKey = "form:$formSession:$route:$selectedId:$catalogPayload",
                                routeKey = "$route:$selectedId:$catalogPayload",
                                lookups = ProductLookups(
                                    option = { definition, name, selected ->
                                        model.write({ selected(createOption(definition, name)) })
                                    },
                                ),
                                loadPhoto = model::readPhoto,
                                onManageCategory = { id ->
                                    selectedCategoryId = id
                                    navigateTo(Screen.CategoryDetail(id))
                                },
                                onChange = { model.error.value = null },
                                onSave = { mode, input ->
                                    val editing =
                                        if (mode == ProductFormMode.Edit) product else null
                                    val selectedPhotos = input.photoUris.map { uri ->
                                        com.nadim.bikepart.shop.PhotoChange.Import {
                                            resolver.openInputStream(uri.toUri())
                                                ?: throw com.nadim.bikepart.shop.ShopValidation(
                                                    com.nadim.bikepart.shop.ProductField.Photo,
                                                    "Photo unavailable. Choose it again or remove it.",
                                                )
                                        }
                                    }
                                    val keepCatalogPhoto = input.catalog.photo != null && !input.photoRemoved
                                    val photo = if (mode == ProductFormMode.Create && keepCatalogPhoto) {
                                        com.nadim.bikepart.shop.PhotoChange.Keep
                                    } else selectedPhotos.firstOrNull() ?: if (input.photoRemoved) {
                                        com.nadim.bikepart.shop.PhotoChange.Remove
                                    } else {
                                        com.nadim.bikepart.shop.PhotoChange.Keep
                                    }
                                    model.write(
                                        {
                                            when (mode) {
                                                ProductFormMode.Template -> {
                                                    templateId = saveTemplate(
                                                        input.catalog,
                                                        templateId,
                                                        photo
                                                    )
                                                }

                                                ProductFormMode.Edit -> {
                                                    val existing = requireNotNull(editing)
                                                    editProduct(
                                                        existing.id,
                                                        input.catalog.title,
                                                        input.catalog.categoryId,
                                                        input.catalog.description,
                                                        input.catalog.attributes,
                                                        input.categoryMappings,
                                                        input.catalog.modelIds,
                                                        input.locationIds,
                                                        photo,
                                                    )
                                                    setSellingPrice(existing.id, input.priceRupees)
                                                }

                                                ProductFormMode.Create -> {
                                                    selectedId = createFromCatalog(
                                                        input.catalog,
                                                        input.openingPieces,
                                                        input.priceRupees,
                                                        input.allowSimilar,
                                                        input.locationIds,
                                                        photo,
                                                        additionalPhotos = if (keepCatalogPhoto) selectedPhotos else selectedPhotos.drop(1),
                                                    )
                                                }
                                            }
                                        },
                                        {
                                            navigateTo(
                                                if (mode == ProductFormMode.Template) {
                                                    Screen.TemplateDetail
                                                } else {
                                                    Screen.Detail
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                            androidx.compose.runtime.CompositionLocalProvider(
                                LocalShopRouteBack provides ::back,
                            ) {
                                ShopNavigationDisplay(
                                    backStack = backStack,
                                    onBack = { if (backStack.size > 1) back() },
                                    modifier = Modifier.fillMaxSize(),
                                    content = { key ->
                                    when (key) {
                                        is Screen.Home -> {
                                            HomeRoute(
                                                shop = shop,
                                                busy = busy,
                                                onAdd = ::addProduct,
                                                onStock = { openStock(it) },
                                                onNewSale = ::newCart,
                                                onCarts = { selectTab(Screen.Carts) },
                                                onReceive = { navigateTo(Screen.ReceiveProducts) },
                                                onSales = { navigateTo(Screen.Sales) },
                                                onSale = {
                                                    selectedId = it
                                                    model.error.value = null
                                                    navigateTo(Screen.Receipt)
                                                },
                                                searchBar = searchBarContent,
                                            )
                                        }
                                        is Screen.Stock -> {
                                            StockRoute(
                                                shop = shop,
                                                busy = busy,
                                                stateHolder = stockState,
                                                session = stockSession,
                                                initialHealth = stockHealth,
                                                initialQuery = stockQuery,
                                                loadPhoto = model::readPhoto,
                                                onAdd = ::addProduct,
                                                onOpen = {
                                                    selectedId = it; navigateTo(Screen.Detail)
                                                },
                                                onScan = { scan(ScanPurpose.Catalog, Screen.Stock) },
                                                onProductAction = { item, action ->
                                                    selectedId = item.id
                                                    model.error.value = null
                                                    when (action) {
                                                        "edit" -> navigateTo(Screen.Edit)
                                                        "copy" -> beginCatalog(
                                                            shop.copyCatalog(item.id),
                                                            Screen.Create
                                                        )

                                                        "labels" -> navigateTo(Screen.Labels)
                                                        "receive" -> receive(item.id)
                                                    }
                                                },
                                            )
                                        }
                                        is Screen.Carts -> {
                                            CartsRoute(shop, busy, ::newCart) {
                                                newCartId = ""
                                                selectedId = it
                                                model.openCart(it)
                                                navigateTo(Screen.Cart)
                                            }
                                        }
                                        is Screen.More -> {
                                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
                                                MoreRoute(
                                                    shop = shop,
                                                    busy = busy,
                                                    onOpenAppearance = { navigateTo(Screen.AppearanceSettings) },
                                                    onDeveloperOption = { navigateTo(Screen.DeveloperOption) },
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
                                        is Screen.AppearanceSettings -> {
                                            AppearanceRoute(
                                                appearance = shop.appearance,
                                                busy = busy,
                                                customization = customization,
                                                onAppearance = { model.write({ setAppearance(it) }) },
                                                onBrand = model.themePreferences::setBrand,
                                                onAccent = model.themePreferences::setAccent,
                                            )
                                        }
                                        is Screen.DeveloperOption -> {
                                            DeveloperOptionRoute(busy = busy) { onAdded ->
                                                model.write(action = { addDummyData() }, done = onAdded)
                                            }
                                        }
                                        is Screen.Backups -> {
                                            BackupsRoute(
                                                model = model,
                                                shop = shop,
                                                localCapture = localCapture,
                                                recoveryCapture = recoveryCapture,
                                                busy = backupBusy,
                                                error = backupError,
                                                saved = backupSaved,
                                                recoveryExport = recoveryExport,
                                            )
                                        }
                                        is Screen.Scan -> {
                                            ScanRoute(
                                                shop = shop,
                                                purpose = ScanPurpose.valueOf(scanPurpose),
                                                busy = busy,
                                                error = error,
                                                camera = camera,
                                                manual = {
                                                    navigateTo(
                                                        when (ScanPurpose.valueOf(scanPurpose)) {
                                                            ScanPurpose.Sale -> Screen.CartProducts
                                                            ScanPurpose.Receiving -> Screen.ReceiveProducts
                                                            ScanPurpose.Catalog, ScanPurpose.ProductDetail -> Screen.CatalogSearch
                                                        },
                                                    )
                                                },
                                                onChange = { model.error.value = null },
                                                add = { id, pieces ->
                                                    cartDraft?.let { draft ->
                                                        model.write(
                                                            {
                                                                addCartProduct(
                                                                    draft.cart.id,
                                                                    draft.cart.editVersion,
                                                                    id,
                                                                    pieces
                                                                )
                                                            },
                                                            {
                                                                model.openCart(draft.cart.id); navigateTo(
                                                                Screen.Cart
                                                            )
                                                            },
                                                        )
                                                    }
                                                },
                                                receive = ::receive,
                                                open = {
                                                    selectedId = it; navigateTo(Screen.Detail)
                                                },
                                                copy = { beginCatalog(it, Screen.Create) },
                                                cart = cartDraft?.cart,
                                                link = { code, target, revision, done ->
                                                    model.write({
                                                        linkBarcode(
                                                            code,
                                                            target,
                                                            expectedAssignmentRevision = revision
                                                        )
                                                    }, done)
                                                },
                                            )
                                        }

                                        is Screen.ReceiveProducts -> {
                                            ReceiveProductsRoute(
                                                shop = shop,
                                                busy = busy,
                                                loadPhoto = model::readPhoto,
                                                onScan = {
                                                    scan(
                                                        ScanPurpose.Receiving,
                                                        Screen.ReceiveProducts
                                                    )
                                                },
                                                onSelect = ::receive,
                                                onUseTemplate = {
                                                    beginCatalog(
                                                        shop.prefillTemplate(
                                                            it
                                                        ), Screen.Create
                                                    )
                                                },
                                                onCreate = { navigateTo(Screen.Create) },
                                            )
                                        }

                                        is Screen.CatalogSearch -> {
                                            CatalogSearchRoute(
                                                shop = shop,
                                                busy = busy,
                                                loadPhoto = model::readPhoto,
                                                onScan = {
                                                    scan(
                                                        ScanPurpose.Catalog,
                                                        Screen.CatalogSearch
                                                    )
                                                },
                                                onSelect = {
                                                    selectedId = it
                                                    navigateTo(Screen.Detail)
                                                },
                                                onUseTemplate = {
                                                    beginCatalog(
                                                        shop.prefillTemplate(
                                                            it
                                                        ), Screen.Create
                                                    )
                                                },
                                                onCreate = { navigateTo(Screen.Create) },
                                            )
                                        }
                                        is Screen.Arrival -> {
                                            ArrivalRoute(
                                                product = product,
                                                operationId = arrivalOperation,
                                                busy = busy,
                                                error = error,
                                                onChange = { model.error.value = null },
                                                save = { pieces, cost ->
                                                    model.write(
                                                        {
                                                            receiveStock(
                                                                arrivalOperation,
                                                                requireNotNull(product).id,
                                                                pieces,
                                                                cost
                                                            )
                                                        },
                                                        { navigateTo(Screen.Detail) },
                                                    )
                                                },
                                            )
                                        }
                                        is Screen.Cart -> {
                                            CartRoute(
                                                shop = shop,
                                                draft = cartDraft,
                                                busy = busy,
                                                error = error,
                                                edit = model::editCart,
                                                pause = ::back,
                                                addProduct = { navigateTo(Screen.CartProducts) },
                                                retry = { model.flushCart() },
                                                reload = model::reloadCart,
                                                checkout = {
                                                    model.flushCart {
                                                        checkoutVersion =
                                                            requireNotNull(model.cartDraft.value).cart.editVersion
                                                        model.error.value = null
                                                        navigateTo(Screen.Checkout)
                                                    }
                                                },
                                                removeProduct = { productId ->
                                                    cartDraft?.let { draft ->
                                                        model.write(
                                                            {
                                                                saveCart(
                                                                    draft.cart.id,
                                                                    draft.cart.editVersion,
                                                                    draft.customer,
                                                                    draft.quantities - productId
                                                                )
                                                            },
                                                            { model.openCart(draft.cart.id) },
                                                        )
                                                    }
                                                },
                                                discard = {
                                                    cartDraft?.let { draft ->
                                                        model.write(
                                                            {
                                                                discardCart(
                                                                    draft.cart.id,
                                                                    draft.cart.editVersion
                                                                )
                                                            },
                                                            {
                                                                newCartId =
                                                                    ""; selectTab(Screen.Carts)
                                                            },
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                        is Screen.Checkout -> {
                                            CheckoutRoute(
                                                shop = shop,
                                                cart = shop.carts.find { it.id == selectedId },
                                                sale = selectedSale,
                                                busy = busy,
                                                error = error,
                                                onChange = { model.error.value = null },
                                                onEdit = { cart ->
                                                    model.openCart(cart.id); navigateTo(
                                                    Screen.Cart
                                                )
                                                },
                                                onComplete = { cart, cash ->
                                                    model.write({
                                                        checkout(
                                                            cart.id,
                                                            checkoutVersion,
                                                            cash
                                                        )
                                                    }, { navigateTo(Screen.Receipt) })
                                                },
                                                onOpenSales = {
                                                    model.error.value = null
                                                    navigateTo(Screen.Sales)
                                                },
                                            )
                                        }
                                        is Screen.Sales -> {
                                            SalesRoute(shop) {
                                                selectedId = it
                                                model.error.value = null
                                                navigateTo(Screen.Receipt)
                                            }
                                        }
                                        is Screen.Receipt -> {
                                            ReceiptRoute(selectedSale) {
                                                model.error.value = null
                                                navigateTo(Screen.Sales)
                                            }
                                        }
                                        is Screen.CartProducts -> {
                                            CartProductsRoute(
                                                shop = shop,
                                                draft = cartDraft,
                                                busy = busy,
                                                error = error,
                                                loadPhoto = model::readPhoto,
                                                onScan = {
                                                    scan(
                                                        ScanPurpose.Sale,
                                                        Screen.CartProducts
                                                    )
                                                },
                                                onChange = { model.error.value = null },
                                                onAdd = { productId, pieces ->
                                                    val draft =
                                                        cartDraft ?: return@CartProductsRoute
                                                    model.write(
                                                        {
                                                            addCartProduct(
                                                                draft.cart.id,
                                                                draft.cart.editVersion,
                                                                productId,
                                                                pieces
                                                            )
                                                        },
                                                        {
                                                            model.openCart(draft.cart.id); navigateTo(
                                                            Screen.Cart
                                                        )
                                                        },
                                                    )
                                                },
                                            )
                                        }

                                        is Screen.Create -> {
                                            CreateProductRoute(formContext, catalog)
                                        }

                                        is Screen.Edit -> {
                                            EditProductRoute(formContext)
                                        }

                                        is Screen.TemplateForm -> {
                                            TemplateFormRoute(
                                                formContext,
                                                catalog,
                                                editing = templateId != null
                                            )
                                        }
                                        is Screen.Templates -> {
                                            TemplateListRoute(
                                                shop = shop,
                                                busy = busy,
                                                onCreate = {
                                                    beginCatalog(
                                                        CatalogDraft(),
                                                        Screen.TemplateForm
                                                    )
                                                },
                                                onOpen = {
                                                    templateId =
                                                        it; navigateTo(Screen.TemplateDetail)
                                                },
                                                onUse = {
                                                    beginCatalog(
                                                        shop.prefillTemplate(it),
                                                        Screen.Create
                                                    )
                                                },
                                            )
                                        }
                                        is Screen.TemplateDetail -> {
                                            TemplateDetailRoute(
                                                shop = shop,
                                                template = template,
                                                busy = busy,
                                                loadPhoto = model::readPhoto,
                                                onEdit = {
                                                    template?.let {
                                                        beginCatalog(
                                                            it.catalog,
                                                            Screen.TemplateForm,
                                                            it.id
                                                        )
                                                    }
                                                },
                                                onUse = {
                                                    template?.let {
                                                        beginCatalog(
                                                            shop.prefillTemplate(
                                                                it.id
                                                            ), Screen.Create
                                                        )
                                                    }
                                                },
                                                onRetire = {
                                                    template?.let { selected ->
                                                        model.write({
                                                            retireTemplate(
                                                                selected.id
                                                            )
                                                        })
                                                    }
                                                },
                                            )
                                        }
                                        is Screen.Correction -> {
                                            CorrectionRoute(
                                                product = product,
                                                operationId = correctionOperation,
                                                kind = CorrectionKind.valueOf(correctionKind),
                                                busy = busy,
                                                error = error,
                                                onChange = { model.error.value = null },
                                                save = { quantity, reason ->
                                                    product?.let { selected ->
                                                        model.write(
                                                            {
                                                                correctStock(
                                                                    correctionOperation,
                                                                    selected.id,
                                                                    CorrectionKind.valueOf(
                                                                        correctionKind
                                                                    ),
                                                                    quantity,
                                                                    reason
                                                                )
                                                            },
                                                            { navigateTo(Screen.Detail) },
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                        is Screen.ArchivedProducts -> {
                                            ArchivedProductsRoute(shop, model::readPhoto) {
                                                selectedId = it
                                                navigateTo(Screen.Detail)
                                            }
                                        }
                                        is Screen.Labels -> {
                                            LabelsRoute(shop, product, busy, error, labelUsb, model)
                                        }
                                        is Screen.Detail -> {
                                            ProductDetailRoute(
                                                shop = shop,
                                                product = product,
                                                busy = busy,
                                                error = error,
                                                loadPhoto = model::readPhoto,
                                                onEdit = { navigateTo(Screen.Edit) },
                                                onManageFields = { navigateTo(Screen.Fields) },
                                                onCopy = {
                                                    product?.let {
                                                        beginCatalog(
                                                            shop.copyCatalog(
                                                                it.id
                                                            ), Screen.Create
                                                        )
                                                    }
                                                },
                                                onMakeTemplate = {
                                                    product?.let {
                                                        beginCatalog(
                                                            shop.copyCatalog(
                                                                it.id
                                                            ), Screen.TemplateForm
                                                        )
                                                    }
                                                },
                                                onCorrection = { kind ->
                                                    model.error.value = null
                                                    correctionKind = kind.name
                                                    correctionOperation =
                                                        java.util.UUID.randomUUID().toString()
                                                    navigateTo(Screen.Correction)
                                                },
                                                onArchive = {
                                                    product?.let {
                                                        model.write({
                                                            archiveProduct(
                                                                it.id
                                                            )
                                                        })
                                                    }
                                                },
                                                onReceive = { product?.let { receive(it.id) } },
                                                onLabels = { navigateTo(Screen.Labels) },
                                                onChange = { model.error.value = null },
                                                onThresholdSave = { pieces ->
                                                    product?.let {
                                                        model.write({
                                                            setLowStockThreshold(
                                                                it.id,
                                                                pieces
                                                            )
                                                        })
                                                    }
                                                },
                                            )
                                        }
                                        is Screen.Fields -> {
                                            CatalogFieldsRoute(
                                                shop = shop,
                                                product = product,
                                                busy = busy,
                                                error = error,
                                                onChange = { model.error.value = null },
                                                actions = CatalogActions(
                                                    custom = { input, done ->
                                                        product?.let { selected ->
                                                            model.write({
                                                                createCustomAttribute(
                                                                    selected.id,
                                                                    input.name,
                                                                    input.type,
                                                                    input.required,
                                                                    input.unit
                                                                )
                                                            }, done)
                                                        }
                                                    },
                                                    promote = { id, name -> model.write({ promoteCustomAttribute(id, name) }) },
                                                    retire = { id -> model.write({ retireAttribute(id) }) },
                                                    retireOption = { id -> model.write({ retireOption(id) }) },
                                                    required = { id, required -> model.write({ setAttributeRequired(id, required) }) },
                                                    replace = { id, input, done ->
                                                        model.write({
                                                            replaceAttribute(
                                                                id,
                                                                input.name,
                                                                input.type,
                                                                input.required,
                                                                input.unit
                                                            )
                                                        }, done)
                                                    },
                                                ),
                                            )
                                        }
                                        is Screen.FindingLookups -> {
                                            FindingLookupsRoute(
                                                shop = shop,
                                                busy = busy,
                                                onRetireMake = { model.write({ retireMake(it) }) },
                                                onRetireModel = { model.write({ retireModel(it) }) },
                                                onRetireLocation = { model.write({ retireLocation(it) }) },
                                                onCreateMake = { model.write({ createMake(it) }) },
                                                onCreateModel = { make, name ->
                                                    model.write({
                                                        createModel(
                                                            make,
                                                            name
                                                        )
                                                    })
                                                },
                                                onCreateLocation = { model.write({ createLocation(it) }) },
                                            )
                                        }
                                        is Screen.Categories -> {
                                            CategoriesRoute(
                                                shop = shop,
                                                busy = busy,
                                                onOpen = { id ->
                                                    selectedCategoryId = id
                                                    navigateTo(Screen.CategoryDetail(id))
                                                },
                                                onRename = { id, name ->
                                                    model.write({ renameCategory(id, name) })
                                                },
                                                onCreate = { name ->
                                                    model.write({
                                                        createCategory(
                                                            name
                                                        )
                                                    })
                                                },
                                            )
                                        }

                                        is Screen.CategoryDetail -> {
                                            CategoryDetailRoute(
                                                shop = shop,
                                                categoryId = selectedCategoryId,
                                                busy = busy,
                                                error = error,
                                                onChange = { model.error.value = null },
                                                actions = CategoryFieldActions(
                                                    create = { input, done ->
                                                        model.write({
                                                            createAttribute(
                                                                categoryId = input.categoryId,
                                                                name = input.name,
                                                                type = input.type,
                                                                required = input.required,
                                                                unit = input.unit,
                                                            )
                                                        }, done)
                                                    },
                                                    createOption = { fieldId, name, done ->
                                                        model.write(
                                                            { createOption(fieldId, name) },
                                                            done
                                                        )
                                                    },
                                                    retire = { model.write({ retireAttribute(it) }) },
                                                    retireOption = { model.write({ retireOption(it) }) },
                                                    required = { id, required ->
                                                        model.write({
                                                            setAttributeRequired(
                                                                id,
                                                                required
                                                            )
                                                        })
                                                    },
                                                    replace = { id, input, done ->
                                                        model.write({
                                                            replaceAttribute(
                                                                id,
                                                                input.name,
                                                                input.type,
                                                                input.required,
                                                                input.unit,
                                                            )
                                                        }, done)
                                                    },
                                                ),
                                            )
                                        }
                                        }
                                    }
                                )
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
