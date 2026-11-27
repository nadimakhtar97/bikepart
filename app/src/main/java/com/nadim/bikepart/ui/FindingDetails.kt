package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.LookupNames
import com.nadim.bikepart.shop.Product
import com.nadim.bikepart.shop.ProductField
import com.nadim.bikepart.shop.ShopSnapshot
import com.nadim.bikepart.ui.theme.ShopSpacing

internal val idSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

@Composable
internal fun FindingFields(shop: ShopSnapshot, product: Product?, models: Set<String>, locations: Set<String>, busy: Boolean,
                           error: ShopError?,
                           assigned: Boolean,
                           useDropdown: Boolean,
                           onAssigned: (Boolean) -> Unit,
                           onModels: (Set<String>) -> Unit,
                           onLocations: (Set<String>) -> Unit,
                           showLocations: Boolean = true
) {
    var make by rememberSaveable { mutableStateOf("") }
    ShopSection(if (showLocations) "Fitment and locations" else "Fitment") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
            ShopChoiceChip(
                label = stringResource(R.string.finding_unknown_fitment),
                selected = !assigned,
                enabled = !busy,
                onClick = {
                    if (useDropdown) make = ""
                    onAssigned(false)
                    onModels(emptySet())
                })
            ShopChoiceChip(
                label = stringResource(R.string.finding_assigned_fitment),
                selected = assigned,
                enabled = !busy,
                onClick = { onAssigned(true) })
        }
        if (assigned) {
            Text(stringResource(R.string.finding_select_model_note), style = MaterialTheme.typography.bodyMedium)
            LookupSelector(
                label = stringResource(R.string.finding_search_make),
                addLabel = stringResource(R.string.finding_add_make),
                options = shop.makes.filter { !it.retired }.map { it.id to it.name },
                selected = setOf(make),
                busy = busy,
                onSelect = { make = it },
                onCreate = null,
                emptyMessage = stringResource(R.string.finding_no_matching_make),
                useDropdown = useDropdown,
                onDropdownRemove = { make = "" },
                dropdownSingleChoice = true,
            )
            val selectedModels = shop.models.filter { it.id in models }.map { model ->
                val parent = shop.makes.single { it.id == model.makeId }
                val retired = if (parent.retired || model.retired) " (retired)" else ""
                model.id to (parent.name + " · " + model.name + retired)
            }
            if (make.isNotEmpty()) key(make) {
                LookupSelector(
                    label = stringResource(R.string.finding_search_model),
                    addLabel = stringResource(R.string.finding_add_model),
                    options = shop.models.filter { it.makeId == make && !it.retired && shop.makes.any { parent -> parent.id == make && !parent.retired } }
                        .map { it.id to it.name },
                    selected = models,
                    busy = busy,
                    onSelect = { onModels(if (it in models) models - it else models + it) },
                    onCreate = null,
                    emptyMessage = stringResource(R.string.finding_no_matching_model),
                    useDropdown = useDropdown,
                    dropdownSelectedOptions = selectedModels,
                )
            }
            if (useDropdown) {
                if (make.isEmpty()) SelectedLookupChips(selectedModels, busy) { onModels(models - it) }
            } else {
                selectedModels.forEach { (id, label) ->
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    val modelName = shop.models.single { it.id == id }.name
                    ShopSecondaryButton(
                        enabled = !busy,
                        onClick = { onModels(models - id) },
                        modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
                    ) { Text(stringResource(R.string.remove) + modelName) }
                }
            }
            if (error?.field == ProductField.Fitment) FormError(error.message)
            if (models.isEmpty()) Text(stringResource(R.string.finding_choose_model_note), style = MaterialTheme.typography.bodyMedium)
        }
        if (showLocations) {
            Text(stringResource(R.string.finding_locations_note), style = MaterialTheme.typography.bodyMedium)
            LookupSelector(
                label = stringResource(R.string.finding_search_location),
                addLabel = stringResource(R.string.finding_add_location),
                options = shop.locations.filter { !it.retired || it.id in locations && it.id in product?.locationIds.orEmpty() }
                    .map { it.id to (it.name + if (it.retired) " (retired)" else "") },
                selected = locations,
                busy = busy,
                onSelect = { onLocations(if (it in locations) locations - it else locations + it) },
                onCreate = null,
                emptyMessage = stringResource(R.string.finding_no_matching_location),
                useDropdown = useDropdown,
            )
            if (error?.field == ProductField.Location) FormError(error.message)
        }
    }
}

@Composable
internal fun FindingSummary(shop: ShopSnapshot, product: Product) {
    ShopSection(stringResource(R.string.finding_fitment_locations)) {
        Text(if (product.modelIds.isEmpty()) "Fitment: Unknown" else "Fitment: Assigned", style = MaterialTheme.typography.bodyMedium)
        shop.models.filter { it.id in product.modelIds }.forEach { model ->
            val make = shop.makes.single { it.id == model.makeId }
            Text(make.name + " · " + model.name + if (make.retired || model.retired) " (retired)" else "", style = MaterialTheme.typography.bodyMedium)
        }
        shop.locations.filter { it.id in product.locationIds }.forEach {
            Text(stringResource(if (it.retired) R.string.location_retired else R.string.location, it.name), style = MaterialTheme.typography.bodyMedium)
        }
        if (product.locationIds.isEmpty()) Text(stringResource(R.string.no_finding_locations_assigned), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun FindingLookupSettings(
    shop: ShopSnapshot,
    busy: Boolean,
    retireMake: (String) -> Unit,
    retireModel: (String) -> Unit,
    retireLocation: (String) -> Unit,
    createMake: (String) -> Unit,
    createModel: (String, String) -> Unit,
    createLocation: (String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var addingMake by rememberSaveable { mutableStateOf(false) }
    var addingModel by rememberSaveable { mutableStateOf(false) }
    var addingLocation by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.finding_retired_names_note), style = MaterialTheme.typography.bodyMedium)
    ShopSearchField(search, { search = it }, "Find make, model or location", enabled = !busy)
    fun matches(name: String) = LookupNames.key(name).contains(LookupNames.key(search))
    ShopSection(stringResource(R.string.finding_motorcycle_makes)) {
        ShopSecondaryButton(
            onClick = { addingMake = true },
            enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
        ) { Text(stringResource(R.string.finding_create_make)) }
        shop.makes.filter { matches(it.name) }.forEach {
            LookupRetirement(it.name, it.retired, busy) { retireMake(it.id) }
        }
        if (shop.makes.isEmpty()) Text(stringResource(R.string.finding_no_makes), style = MaterialTheme.typography.bodyMedium)
        if (addingMake) LookupNameDialog(
            title = stringResource(R.string.finding_create_make),
            fieldLabel = stringResource(R.string.finding_motorcycle_make_name),
            createLabel = stringResource(R.string.finding_create_make),
            busy = busy,
            onDismiss = { addingMake = false },
            onCreate = { createMake(it); search = ""; addingMake = false },
        )
    }
    ShopSection(stringResource(R.string.finding_motorcycle_models)) {
        val activeMakes = shop.makes.filter { !it.retired }
        ShopSecondaryButton(
            onClick = { addingModel = true },
            enabled = !busy && activeMakes.isNotEmpty(),
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
        ) { Text(stringResource(R.string.finding_create_model)) }
        if (activeMakes.isEmpty()) Text(
            stringResource(R.string.finding_create_model_make_required),
            style = MaterialTheme.typography.bodyMedium
        )
        shop.models.forEach { model ->
            val label = shop.makes.single { it.id == model.makeId }.name + " · " + model.name
            if (matches(label)) LookupRetirement(label, model.retired, busy) { retireModel(model.id) }
        }
        if (shop.models.isEmpty()) Text(stringResource(R.string.finding_no_models), style = MaterialTheme.typography.bodyMedium)
        if (addingModel) CreateModelDialog(
            makes = activeMakes,
            busy = busy,
            onDismiss = { addingModel = false },
            onCreate = { makeId, name ->
                createModel(makeId, name); search = ""; addingModel = false
            },
        )
    }
    ShopSection(stringResource(R.string.finding_storage_locations)) {
        ShopSecondaryButton(
            onClick = { addingLocation = true },
            enabled = !busy,
            modifier = Modifier.heightIn(min = ShopSpacing.touchTarget),
        ) { Text(stringResource(R.string.finding_create_location)) }
        shop.locations.filter { matches(it.name) }.forEach {
            LookupRetirement(it.name, it.retired, busy) { retireLocation(it.id) }
        }
        if (shop.locations.isEmpty()) Text(stringResource(R.string.finding_no_locations), style = MaterialTheme.typography.bodyMedium)
        if (addingLocation) LookupNameDialog(
            title = stringResource(R.string.finding_create_location),
            fieldLabel = stringResource(R.string.finding_location_name),
            createLabel = stringResource(R.string.finding_create_location),
            busy = busy,
            onDismiss = { addingLocation = false },
            onCreate = { createLocation(it); search = ""; addingLocation = false },
        )
    }
}

@Composable
private fun LookupRetirement(name: String, retired: Boolean, busy: Boolean, retire: () -> Unit) {
    if (retired) Text(name + " (retired)", style = MaterialTheme.typography.bodyMedium)
    else ShopSecondaryButton(enabled = !busy, onClick = retire, modifier = Modifier.heightIn(min = ShopSpacing.touchTarget)) { Text(stringResource(R.string.retire, name)) }
}
