package com.nadim.bikepart.ui.theme

import androidx.compose.runtime.Composable
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.*
import androidx.compose.ui.graphics.SolidColor
import com.microsoft.fluentui.theme.token.FluentAliasTokens
import com.microsoft.fluentui.theme.token.StateBrush
import com.microsoft.fluentui.theme.token.controlTokens.TextFieldInfo
import com.microsoft.fluentui.theme.token.controlTokens.TextFieldTokens
import com.microsoft.fluentui.theme.token.controlTokens.ToggleSwitchInfo
import com.microsoft.fluentui.theme.token.controlTokens.ToggleSwitchTokens

/** Native geometry/interactions with measured accessibility adaptations, scoped with the theme. */
internal class ShopControlTokens : ControlTokens() {
    init {
        updateToken(ControlType.ToggleSwitchControlType, AccessibleToggleSwitchTokens())
        updateToken(ControlType.TextFieldControlType, AccessibleTextFieldTokens())
    }
}

private class AccessibleTextFieldTokens : TextFieldTokens() {
    @Composable
    override fun dividerColor(textFieldInfo: TextFieldInfo): androidx.compose.ui.graphics.Brush {
        if (textFieldInfo.isStatusError || textFieldInfo.isFocused) return super.dividerColor(
            textFieldInfo
        )
        val accessible = FluentTheme.aliasTokens.neutralStrokeColor[
            FluentAliasTokens.NeutralStrokeColorTokens.StrokeAccessible].value(FluentTheme.themeMode)
        return SolidColor(accessible)
    }
}

private class AccessibleToggleSwitchTokens : ToggleSwitchTokens() {
    @Composable
    override fun trackColor(switchInfo: ToggleSwitchInfo): StateColor {
        val native = super.trackColor(switchInfo)
        // The native unchecked track has no outline: only 1.01–1.83:1 on actual shop groups.
        val accessible = FluentTheme.aliasTokens.neutralStrokeColor[
            FluentAliasTokens.NeutralStrokeColorTokens.StrokeAccessible].value(FluentTheme.themeMode)
        return StateColor(rest = accessible, pressed = accessible, focused = accessible,
            selected = native.selected, selectedPressed = native.selectedPressed, selectedFocused = native.selectedFocused,
            disabled = native.disabled, selectedDisabled = native.selectedDisabled)
    }

    @Composable
    override fun knobColor(switchInfo: ToggleSwitchInfo): StateColor {
        val native = super.knobColor(switchInfo)
        // White-on-native-dark-brand tracks measures as low as 2.08:1. Use the SDK on-color role.
        val foreground = FluentTheme.aliasTokens.neutralForegroundColor[
            FluentAliasTokens.NeutralForegroundColorTokens.ForegroundOnColor].value(FluentTheme.themeMode)
        return StateColor(rest = foreground, pressed = foreground, focused = foreground,
            selected = foreground, selectedPressed = foreground, selectedFocused = foreground,
            disabled = native.disabled, selectedDisabled = native.selectedDisabled)
    }
}
