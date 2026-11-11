package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

internal class FindingLookups(
    val make: (String, (String) -> Unit) -> Unit,
    val model: (String, String, (String) -> Unit) -> Unit,
    val location: (String, (String) -> Unit) -> Unit,
)

internal val idSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

@Composable
internal fun FindingFields(shop: ShopSnapshot, product: Product?, models: Set<String>, locations: Set<String>, busy: Boolean,
    error: ShopError?, lookups: FindingLookups, assigned: Boolean, onAssigned: (Boolean) -> Unit, onModels: (Set<String>) -> Unit, onLocations: (Set<String>) -> Unit) {
    var make by rememberSaveable { mutableStateOf("") }
    ShopSection("Fitment and locations") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            FilterChip(selected = !assigned, enabled = !busy, onClick = { onAssigned(false); onModels(emptySet()) },
                label = { Text("Unknown fitment") }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
            FilterChip(selected = assigned, enabled = !busy, onClick = { onAssigned(true) },
                label = { Text("Assigned fitment") }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget))
        }
        if (assigned) {
            Text("Select at least one motorcycle model. Compatibility is assigned by staff.", style = MaterialTheme.typography.bodyMedium)
            LookupSelector("Search or add make", "Add make", shop.makes.filter { !it.retired }.map { it.id to it.name }, setOf(make), busy,
                onSelect = { make = it }, onCreate = { name -> lookups.make(name) { make = it } })
            if (make.isNotEmpty()) key(make) {
                LookupSelector("Search or add model", "Add model", shop.models.filter { it.makeId == make && !it.retired && shop.makes.any { parent -> parent.id == make && !parent.retired } }.map { it.id to it.name }, models, busy,
                    onSelect = { onModels(if (it in models) models - it else models + it) },
                    onCreate = { name -> lookups.model(make, name) { onModels(models + it) } })
            }
            shop.models.filter { it.id in models }.forEach { model ->
                val parent = shop.makes.single { it.id == model.makeId }
                Text(parent.name + " · " + model.name + if (parent.retired || model.retired) " (retired)" else "", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(enabled = !busy, onClick = { onModels(models - model.id) }, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Remove " + model.name) }
            }
            if (error?.field == ProductField.Fitment) FormError(error.message)
            if (models.isEmpty()) Text("Choose a model before saving assigned fitment.", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Finding locations · one combined stock count", style = MaterialTheme.typography.bodyMedium)
        LookupSelector("Search or add location", "Add location", shop.locations.filter { !it.retired || it.id in locations && it.id in product?.locationIds.orEmpty() }.map { it.id to (it.name + if (it.retired) " (retired)" else "") }, locations, busy,
            onSelect = { onLocations(if (it in locations) locations - it else locations + it) },
            onCreate = { name -> lookups.location(name) { onLocations(locations + it) } })
        if (error?.field == ProductField.Location) FormError(error.message)
    }
}

@Composable
internal fun FindingSummary(shop: ShopSnapshot, product: Product) {
    ShopSection("Fitment and locations") {
        Text(if (product.modelIds.isEmpty()) "Fitment: Unknown" else "Fitment: Assigned", style = MaterialTheme.typography.bodyMedium)
        shop.models.filter { it.id in product.modelIds }.forEach { model ->
            val make = shop.makes.single { it.id == model.makeId }
            Text(make.name + " · " + model.name + if (make.retired || model.retired) " (retired)" else "", style = MaterialTheme.typography.bodyMedium)
        }
        shop.locations.filter { it.id in product.locationIds }.forEach {
            Text("Location: " + it.name + if (it.retired) " (retired)" else "", style = MaterialTheme.typography.bodyMedium)
        }
        if (product.locationIds.isEmpty()) Text("No finding locations assigned", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FindingLookupSettings(shop: ShopSnapshot, busy: Boolean, retireMake: (String) -> Unit,
    retireModel: (String) -> Unit, retireLocation: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Text("Retired names stay readable on existing products and cannot be newly selected.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(search, { search = it }, enabled = !busy, label = { Text("Find make, model or location") }, modifier = Modifier.fillMaxWidth())
    fun matches(name: String) = LookupNames.key(name).contains(LookupNames.key(search))
    ShopSection("Motorcycle makes") {
        shop.makes.filter { matches(it.name) }.forEach {
            LookupRetirement(it.name, it.retired, busy) { retireMake(it.id) }
        }
        if (shop.makes.isEmpty()) Text("No makes added", style = MaterialTheme.typography.bodyMedium)
    }
    ShopSection("Motorcycle models") {
        shop.models.forEach { model ->
            val label = shop.makes.single { it.id == model.makeId }.name + " · " + model.name
            if (matches(label)) LookupRetirement(label, model.retired, busy) { retireModel(model.id) }
        }
        if (shop.models.isEmpty()) Text("No models added", style = MaterialTheme.typography.bodyMedium)
    }
    ShopSection("Storage locations") {
        shop.locations.filter { matches(it.name) }.forEach {
            LookupRetirement(it.name, it.retired, busy) { retireLocation(it.id) }
        }
        if (shop.locations.isEmpty()) Text("No locations added", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LookupRetirement(name: String, retired: Boolean, busy: Boolean, retire: () -> Unit) {
    if (retired) Text(name + " (retired)", style = MaterialTheme.typography.bodyMedium)
    else OutlinedButton(enabled = !busy, onClick = retire, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text("Retire: " + name) }
}
