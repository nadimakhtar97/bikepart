package com.nadim.bikepart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.BikepartTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShopApp() }
    }
}

class ShopViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private var shop: Shop? = null
    val state = kotlinx.coroutines.flow.MutableStateFlow<ShopSnapshot?>(null)
    val error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    init { viewModelScope.launch {
        try {
            val opened = Shop.open(application)
            shop = opened
            opened.snapshots.collect { state.value = it }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = "Could not open shop. Reopen the app to retry." }
    } }
    fun write(action: suspend Shop.() -> Unit, done: () -> Unit = {}) {
        if (busy.value || shop == null) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try { action(requireNotNull(shop)); state.value = requireNotNull(shop).snapshot(); done() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.message ?: "Check the entered values" }
            catch (e: Exception) { error.value = "Could not save. Try again." }
            finally { busy.value = false }
        }
    }
    override fun onCleared() { shop?.close() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopApp(model: ShopViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf("Home") }
    var route by rememberSaveable { mutableStateOf("main") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    val product = state?.products?.find { it.id == selectedId }
    fun back() { route = if (route == "edit") "detail" else "main"; model.error.value = null }
    BackHandler(route != "main") { if (!busy) back() }
    BikepartTheme(appearance = state?.appearance ?: Appearance.System) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(when(route) { "create" -> "New product"; "detail" -> "Product"; "edit" -> "Edit details"; "categories" -> "Categories"; else -> destination }) },
                navigationIcon = { if (route != "main") TextButton(enabled = !busy, onClick = { back() }) { Text("Back") } }) },
            bottomBar = { if (route == "main") NavigationBar {
                listOf("Home", "Stock", "Carts", "More").forEachIndexed { index, name ->
                    NavigationBarItem(selected = destination == name, onClick = { destination = name; model.error.value = null },
                        icon = { Icon(painterResource(listOf(R.drawable.ic_home, R.drawable.ic_stock, R.drawable.ic_carts, R.drawable.ic_more)[index]), contentDescription = null) }, label = { Text(name) })
                }
            } }
        ) { padding ->
            val scrollState = key(route, destination, selectedId) { rememberScrollState() }
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state == null) { Text(error ?: "Opening your shop…"); return@Column }
                val shop = requireNotNull(state)
                when (route) {
                    "create", "edit" -> key(route, selectedId) { ProductForm(shop, if (route == "edit") product else null, busy, error,
                        onChange = { model.error.value = null },
                        save = { title, category, description, quantity, price, distinct ->
                            val editing = if (route == "edit") product else null
                            model.write({
                                if (editing != null) editProduct(editing.id, title, category, description)
                                else selectedId = createProduct(title, category, description, quantity, price, distinct)
                            }, { route = "detail" })
                        }) }
                    "detail" -> if (product != null) {
                        Text(product.title, style = MaterialTheme.typography.headlineMedium)
                        Text("SKU: ${product.sku}")
                        Text("Product ID: ${product.id}")
                        Text(shop.categories.single { it.id == product.categoryId }.name)
                        Text(product.description.ifBlank { "No description" })
                        Text("${indianNumber(product.quantity)} pieces", style = MaterialTheme.typography.headlineSmall)
                        Text(if (product.quantity == 0L) "Out of stock" else "In stock")
                        Text("Selling price: ₹${indianNumber(product.priceRupees)}")
                        Text("Opening stock: ${indianNumber(product.quantity)} pieces")
                        if (product.quantity == 0L) Text("No stock movements")
                        Button(onClick = { route = "edit" }) { Text("Edit details") }
                    }
                    "categories" -> shop.categories.forEach { category ->
                        key(category.id) {
                            var name by rememberSaveable(category.name) { mutableStateOf(category.name) }
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(enabled = !busy && name != category.name, onClick = { model.write({ renameCategory(category.id, name) }) }) { Text("Save ${category.name}") }
                        }
                    }
                    else -> when (destination) {
                        "Home" -> {
                            Text("Your parts counter", style = MaterialTheme.typography.headlineMedium)
                            Text("${shop.products.size} stocked products · works offline")
                            Button(onClick = { route = "create" }) { Text("Add product") }
                            OutlinedButton(onClick = { destination = "Stock" }) { Text("View stock") }
                            if (shop.products.isEmpty()) Text("Start with your first product. Record its opening stock and selling price.")
                            shop.products.filter { it.quantity == 0L }.forEach { Text("Out of stock: ${it.title}") }
                        }
                        "Stock" -> {
                            Button(onClick = { route = "create" }) { Text("Add product") }
                            if (shop.products.isEmpty()) Text("No products yet")
                            shop.products.forEach { item ->
                                ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text("${indianNumber(item.quantity)} pieces · ₹${indianNumber(item.priceRupees)}\n${if (item.quantity == 0L) "Out of stock" else "In stock"}\n${item.sku}") }, modifier = Modifier.clickable { selectedId = item.id; route = "detail" }.heightIn(min = 48.dp))
                                HorizontalDivider()
                            }
                        }
                        "Carts" -> Text("No unfinished carts")
                        "More" -> {
                            Text("Appearance", style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.selectableGroup()) { Appearance.entries.forEach { mode ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = shop.appearance == mode, role = Role.RadioButton, onClick = { model.write({ setAppearance(mode) }) })) {
                                    RadioButton(selected = shop.appearance == mode, onClick = null)
                                    Text(mode.name, Modifier.padding(12.dp))
                                }
                            } }
                            Text("Shop teal · fixed across wallpapers")
                            OutlinedButton(onClick = { route = "categories" }) { Text("Edit categories") }
                        }
                    }
                }
                if (error != null && route != "create" && route != "edit") Text(requireNotNull(error), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProductForm(state: ShopSnapshot, product: Product?, busy: Boolean, error: String?, onChange: () -> Unit, save: (String, String, String, String, String, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf(product?.title ?: "") }
    var category by rememberSaveable { mutableStateOf(product?.categoryId ?: "") }
    var description by rememberSaveable { mutableStateOf(product?.description ?: "") }
    var quantity by rememberSaveable { mutableStateOf("0") }
    var price by rememberSaveable { mutableStateOf("") }
    var distinct by rememberSaveable { mutableStateOf(false) }
    Text("Basic details", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(title, { title = it; distinct = false; onChange() }, label = { Text("Title (required)") }, isError = error == "Enter a product title", supportingText = { if (error == "Enter a product title") Text(error) }, modifier = Modifier.fillMaxWidth())
    Text("Category (required)")
    Column(Modifier.selectableGroup()) { state.categories.forEach { item ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = category == item.id, role = Role.RadioButton, onClick = { category = item.id; distinct = false; onChange() })) {
            RadioButton(selected = category == item.id, onClick = null)
            Text(item.name, Modifier.padding(12.dp))
        }
    } }
    if (error == "Choose a category") Text(error, color = MaterialTheme.colorScheme.error)
    OutlinedTextField(description, { description = it; onChange() }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
    if (product == null) {
        Text("Opening stock and price", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(quantity, { quantity = it; onChange() }, label = { Text("Opening stock (pieces)") }, isError = error?.startsWith("Opening stock") == true, supportingText = { if (error?.startsWith("Opening stock") == true) Text(error) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it; onChange() }, label = { Text("Selling price (whole rupees)") }, isError = error?.startsWith("Selling price") == true, supportingText = { if (error?.startsWith("Selling price") == true) Text(error) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
    }
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive; error(error) })
        if (error.startsWith("A similar product")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { distinct = !distinct }) {
                Checkbox(checked = distinct, onCheckedChange = { distinct = it })
                Text("Save as a distinct product", Modifier.padding(top = 12.dp))
            }
        }
    }
    Button(enabled = !busy, onClick = { save(title, category, description, quantity, price, distinct) }) { Text(if (busy) "Saving…" else "Save product") }
}
