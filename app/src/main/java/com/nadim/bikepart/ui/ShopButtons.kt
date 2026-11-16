package com.nadim.bikepart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.ControlTokens.ControlType
import com.microsoft.fluentui.theme.token.controlTokens.*
import com.nadim.bikepart.ui.theme.ShopSpacing

@Composable
internal fun ShopPrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    ShopFluentButton(onClick, modifier.fillMaxWidth(), enabled, ButtonStyle.Button) { Text(label) }
}

/** Fluent's native button ellipsizes at one line and exports editable-text semantics.
 * This scoped fallback retains wrapping slots and ordinary Button semantics while using its tokens.
 */
@Composable
internal fun ShopFluentButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean,
    style: ButtonStyle, shape: Shape? = null, colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    val token = FluentTheme.controlTokens.tokens[ControlType.ButtonControlType] as ButtonTokens
    val info = ButtonInfo(style, ButtonSize.Large)
    val interaction = remember { MutableInteractionSource() }
    val outline = token.borderStroke(info).getBorderStrokeByState(enabled, false, interaction)
    val buttonShape = shape ?: RoundedCornerShape(token.cornerRadius(info))
    var border: Modifier = Modifier
    // Match the SDK's nested focus rings; strokes remain visible on both the fill and parent.
    var width = androidx.compose.ui.unit.Dp(0f)
    outline.forEach { stroke -> width += stroke.width; border = border.border(width, stroke.brush, buttonShape) }
    val background = token.backgroundBrush(info).getBrushByState(enabled, false, interaction)
    val foreground = token.textColor(info).getColorByState(enabled, false, interaction)
    val customBackground = colors?.let { if (enabled) it.containerColor else it.disabledContainerColor }
    val customForeground = colors?.let { if (enabled) it.contentColor else it.disabledContentColor }
    val fill = if (customBackground != null) Modifier.background(customBackground, buttonShape) else Modifier.background(background, buttonShape)
    Row(modifier.heightIn(min = ShopSpacing.touchTarget).then(fill).clip(buttonShape)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .then(border).padding(token.padding(info)),
        horizontalArrangement = Arrangement.spacedBy(token.spacing(info), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalContentColor provides (customForeground ?: foreground)) {
            ProvideTextStyle(token.typography(info)) { content() }
        }
    }
}

/** Passive native-token geometry: the labelled row owns selection and its sole action. */
@Composable
internal fun ShopAppearanceIndicator(selected: Boolean, enabled: Boolean, interaction: MutableInteractionSource) {
    val token = FluentTheme.controlTokens.tokens[ControlType.RadioButtonControlType] as RadioButtonTokens
    val info = RadioButtonInfo(selected)
    val stroke = token.backgroundBrush(info).getBrushByState(enabled, selected, interaction)
    val dot = token.iconColor(info).getColorByState(enabled, selected, interaction)
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        drawCircle(stroke, (token.outerCircleRadius - token.strokeWidthInwards / 2).toPx(),
            style = androidx.compose.ui.graphics.drawscope.Stroke(token.strokeWidthInwards.toPx()))
        if (selected) drawCircle(dot, token.innerCircleRadius.toPx())
    }
}
