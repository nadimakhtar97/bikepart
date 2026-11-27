package com.nadim.bikepart.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.shop.LookupNames
import com.nadim.bikepart.ui.theme.LocalShopFluentStyle
import com.nadim.bikepart.ui.theme.ShopSpacing
import com.microsoft.fluentui.theme.token.FluentStyle
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.StateColor
import com.microsoft.fluentui.theme.token.controlTokens.BasicChipInfo
import com.microsoft.fluentui.theme.token.controlTokens.BasicChipTokens
import com.microsoft.fluentui.tokenized.controls.BasicChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchLookupDropdown(
    label: String,
    emptyMessage: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    singleChoice: Boolean = false,
    selectedOptions: List<Pair<String, String>> = options.filter { it.first in selected },
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val hasSingleSelection = singleChoice && selectedOptions.isNotEmpty()

    fun dismiss() {
        focusManager.clearFocus()
        expanded = false
        query = ""
    }

    val menuExpanded = expanded && !hasSingleSelection
    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { if (!it && !busy) dismiss() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        ShopSearchField(
            value = if (expanded) query else "",
            onValueChange = { query = it; expanded = true },
            label = label,
            enabled = !busy && !hasSingleSelection,
            onSearchFocusChanged = { focused -> if (!busy) expanded = focused },
            // The search field already opens the menu on focus.
            inputModifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = false),
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { if (!busy) dismiss() },
            modifier = Modifier.heightIn(max = 240.dp),
        ) {
            val queryKey = LookupNames.key(query)
            val matches = options.filter { LookupNames.key(it.second).contains(queryKey) }
            if (matches.isEmpty()) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(ShopSpacing.medium),
                )
            }
            matches.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    modifier = Modifier.semantics { this.selected = id in selected },
                    enabled = !busy,
                    onClick = {
                        onSelect(id)
                        dismiss()
                    },
                )
            }
        }
    }
    SelectedLookupChips(selectedOptions, busy, onRemove)
}

@Composable
internal fun SelectedLookupChips(
    options: List<Pair<String, String>>,
    busy: Boolean,
    onRemove: (String) -> Unit,
) {
    if (options.isEmpty()) return
    val branded = LocalShopFluentStyle.current == FluentStyle.Brand
    val colors = MaterialTheme.colorScheme
    val tokens = remember(colors.primaryContainer, colors.onPrimaryContainer) {
        SelectedLookupChipTokens(colors.primaryContainer, colors.onPrimaryContainer)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ShopSpacing.small),
        verticalArrangement = Arrangement.spacedBy(ShopSpacing.small),
    ) {
        options.forEach { (id, name) ->
            BasicChip(
                label = name,
                selected = true,
                enabled = !busy,
                modifier = Modifier
                    .heightIn(min = ShopSpacing.touchTarget)
                    .clickable(
                        enabled = !busy,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.lookup_remove_named, name),
                        onClick = { onRemove(id) },
                    ),
                basicChipTokens = tokens.takeIf { branded },
                trailingAccessory = {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = null,
                        tint = if (branded && !busy) colors.onPrimaryContainer else LocalContentColor.current,
                    )
                },
            )
        }
    }
}

/** Use the shared Fluent-derived accent surface for removable lookup selections. */
private class SelectedLookupChipTokens(
    private val fill: Color,
    private val foreground: Color,
) : BasicChipTokens() {
    @Composable
    override fun backgroundBrush(basicChipInfo: BasicChipInfo): StateBrush {
        val native = super.backgroundBrush(basicChipInfo)
        val selected = SolidColor(fill)
        return StateBrush(
            selected = selected,
            selectedPressed = selected,
            selectedFocused = selected,
            selectedDisabled = native.selectedDisabled,
        )
    }

    @Composable
    override fun textColor(basicChipInfo: BasicChipInfo): StateColor {
        val native = super.textColor(basicChipInfo)
        return StateColor(
            selected = foreground,
            selectedPressed = foreground,
            selectedFocused = foreground,
            selectedDisabled = native.selectedDisabled,
        )
    }
}
