package com.nadim.bikepart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.ControlTokens.ControlType
import com.microsoft.fluentui.theme.token.controlTokens.*
import com.nadim.bikepart.shop.Appearance
import com.nadim.bikepart.ui.theme.BikepartTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/** Measurement tool: resolves the production theme/control scope on Android, not a workflow substitute. */
class ThemeContrastExportTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exportEffectiveThemePairs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pairs = JSONArray()
        compose.setContent {
            for (mode in listOf(Appearance.Light, Appearance.Dark)) BikepartTheme(mode) {
                val c = MaterialTheme.colorScheme
                val resolved = JSONArray()
                fun pair(name: String, foreground: Color, background: Color, minimum: Double = 4.5) {
                    fun Color.hex() = String.format("%06X", toArgb() and 0xffffff)
                    val bg = background.compositeOver(c.surface)
                    resolved.put(JSONObject().put("name", name).put("foreground", foreground.compositeOver(bg).hex())
                        .put("background", bg.hex()).put("minimum", minimum))
                }
                for ((name, fg, bg) in listOf(
                    Triple("primary", c.onPrimary, c.primary), Triple("secondary", c.onSecondary, c.secondary),
                    Triple("tertiary", c.onTertiary, c.tertiary), Triple("error", c.onError, c.error),
                    Triple("primaryContainer", c.onPrimaryContainer, c.primaryContainer),
                    Triple("secondaryContainer", c.onSecondaryContainer, c.secondaryContainer),
                    Triple("errorContainer", c.onErrorContainer, c.errorContainer),
                    Triple("background", c.onBackground, c.background), Triple("surface", c.onSurface, c.surface),
                    Triple("surfaceVariant", c.onSurfaceVariant, c.surfaceVariant),
                    Triple("inverse", c.inverseOnSurface, c.inverseSurface))) pair("bridge.$name", fg, bg)
                for ((name, surface) in listOf("surface" to c.surface, "low" to c.surfaceContainerLow,
                    "container" to c.surfaceContainer, "high" to c.surfaceContainerHigh, "highest" to c.surfaceContainerHighest)) {
                    pair("bridge.$name.text", c.onSurface, surface)
                    pair("bridge.$name.link", c.primary, surface)
                    pair("bridge.$name.error", c.error, surface)
                    pair("bridge.$name.outline", c.outline, surface, 3.0)
                }
                val group = c.primary.copy(alpha = .08f).compositeOver(c.surface)
                pair("bridge.group.text", c.onSurface, group)
                pair("bridge.group.supporting", c.onSurfaceVariant, group)
                pair("bridge.group.link", c.primary, group)
                pair("bridge.group.error", c.error, group)
                pair("bridge.group.outline", c.outline, group, 3.0)
                val radio = FluentTheme.controlTokens.tokens[ControlType.RadioButtonControlType] as RadioButtonTokens
                for (selected in listOf(false, true)) {
                    val info = RadioButtonInfo(selected)
                    val stroke = radio.backgroundBrush(info)
                    pair("radio.$selected.stroke", ((if (selected) stroke.selected else stroke.rest) as SolidColor).value, group, 3.0)
                    if (selected) pair("radio.selected.dot", radio.iconColor(info).selected, group, 3.0)
                }
                val tokens = FluentTheme.controlTokens.tokens[ControlType.ButtonControlType] as ButtonTokens
                for ((name, style) in listOf("primary" to ButtonStyle.Button, "secondary" to ButtonStyle.OutlinedButton)) {
                    val info = ButtonInfo(style, ButtonSize.Large)
                    val text = tokens.textColor(info)
                    val fill = tokens.backgroundBrush(info)
                    val borders = tokens.borderStroke(info)
                    for ((state, fg, brush) in listOf(Triple("rest", text.rest, fill.rest),
                        Triple("pressed", text.pressed, fill.pressed), Triple("focused", text.focused, fill.focused),
                        Triple("selected", text.selected, fill.selected))) {
                        pair("button.$name.$state", fg, (brush as SolidColor).value)
                        pair("button.$name.$state.group", fg, brush.value.compositeOver(group))
                    }
                    // Disabled controls are exempt from WCAG contrast; export them visibly with an explicit exemption.
                    pair("button.$name.disabled.exempt", text.disabled, (fill.disabled as SolidColor).value, 0.0)
                    for ((i, border) in borders.focused.withIndex()) pair("button.$name.focus.$i",
                        (border.brush as SolidColor).value,
                        if (i == 0) c.surface else (borders.focused[i - 1].brush as SolidColor).value, 3.0)
                }
                SideEffect { synchronized(pairs) { pairs.put(JSONObject().put("appearance", mode.name.lowercase()).put("pairs", resolved)) } }
            }
        }
        compose.waitForIdle()
        val fingerprint = instrumentation.context.assets.open("theme-source-sha256.txt").bufferedReader().readText().trim()
        val json = JSONObject().put("sourceSha256", fingerprint).put("themes", pairs)
        val output = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "fluent-contrast.json")
        output.writeText(json.toString(2))
        instrumentation.uiAutomation.executeShellCommand("cp ${output.absolutePath} /data/local/tmp/fluent-contrast.json").close()
    }
}
