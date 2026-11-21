package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.MotorcycleMake
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun LookupNameDialog(
    title: String,
    fieldLabel: String,
    createLabel: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = { ShopTextField(name, { name = it }, fieldLabel, enabled = !busy) },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) { Text(createLabel) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
internal fun CreateModelDialog(
    makes: List<MotorcycleMake>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var makeId by rememberSaveable { mutableStateOf(makes.firstOrNull()?.id.orEmpty()) }
    val selectedMakeId = makeId.takeIf { id -> makes.any { it.id == id } }
        ?: makes.firstOrNull()?.id.orEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.finding_create_model_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = ShopSpacing.formWidth)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ShopSpacing.small),
            ) {
                Text(stringResource(R.string.finding_create_model_make))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small)) {
                    makes.forEach { make ->
                        ShopChoiceChip(
                            label = make.name,
                            selected = make.id == selectedMakeId,
                            enabled = !busy,
                            onClick = { makeId = make.id },
                        )
                    }
                }
                ShopTextField(
                    name,
                    { name = it },
                    stringResource(R.string.finding_motorcycle_model_name),
                    enabled = !busy && makes.isNotEmpty(),
                )
                if (makes.isEmpty()) Text(stringResource(R.string.finding_create_model_make_required))
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && selectedMakeId.isNotEmpty() && name.isNotBlank(),
                onClick = { onCreate(selectedMakeId, name.trim()) },
            ) { Text(stringResource(R.string.finding_create_model)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
