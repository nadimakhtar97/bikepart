package com.nadim.bikepart.ui.theme

import com.microsoft.fluentui.theme.token.*

/** Keep the complete demo definitions intact; adapt only a measured inaccessible semantic role. */
internal fun shopAccessibleAliasTokens(native: IAliasTokens): IAliasTokens =
    if (native is M365AliasTokens) AccessibleM365AliasTokens(native) else native

private class AccessibleM365AliasTokens(private val native: IAliasTokens) : IAliasTokens by native {
    override val brandForegroundColor = TokenSet<FluentAliasTokens.BrandForegroundColorTokens, FluentColor> { role ->
        val source = native.brandForegroundColor[role]
        // Native M365 Color100 is 3.62:1 on Background3 / 4.10:1 on Background2.
        // Its existing Color110 is the next ramp step: one role feeds native controls and the bridge.
        if (role == FluentAliasTokens.BrandForegroundColorTokens.BrandForeground1)
            source.copy(dark = native.brandColor[FluentAliasTokens.BrandColorTokens.Color110]) else source
    }
}
