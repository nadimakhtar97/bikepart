package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.R
import com.nadim.bikepart.ShopError
import com.nadim.bikepart.shop.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun ShopEmptyState(title: String, description: String, icon: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = ShopSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShopSpacing.content)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(painterResource(icon), contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(ShopSpacing.content).size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp))
    }
}

@Composable
internal fun FormError(message: String) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { requester.bringIntoView() }
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.bringIntoViewRequester(requester).semantics { liveRegion = LiveRegionMode.Assertive; error(message) })
}
