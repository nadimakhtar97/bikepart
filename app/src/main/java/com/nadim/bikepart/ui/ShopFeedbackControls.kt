package com.nadim.bikepart.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import com.nadim.bikepart.ui.theme.shopIconPainter as painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nadim.bikepart.ui.theme.ShopSpacing

/** Keeps one tone-to-role mapping for chips and status tiles. */
@Composable
private fun shopStatusColors(tone: ShopStatusTone): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        ShopStatusTone.Neutral -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        ShopStatusTone.Accent -> colors.secondaryContainer to colors.onSecondaryContainer
        ShopStatusTone.Warning -> colors.secondaryFixed to colors.onSecondaryFixedVariant
        ShopStatusTone.Danger -> colors.errorContainer to colors.onErrorContainer
        ShopStatusTone.Success -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
}

@Composable
internal fun ShopStatusChip(label: String, tone: ShopStatusTone = ShopStatusTone.Neutral, modifier: Modifier = Modifier) {
    val (container, content) = shopStatusColors(tone)
    Surface(modifier.heightIn(min = 24.dp), shape = RoundedCornerShape(8.dp), color = container, contentColor = content) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

internal enum class ShopStatusTone { Neutral, Accent, Warning, Danger, Success }

/**
 * An icon tile that carries the same status tone as [ShopStatusChip], so attention
 * rows read at a glance without relying on copy alone.
 */
@Composable
internal fun ShopStatusIconTile(icon: Int, tone: ShopStatusTone, modifier: Modifier = Modifier) {
    val (container, content) = shopStatusColors(tone)
    ShopIconTile(icon, modifier, container = container, contentColor = content)
}
