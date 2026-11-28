package com.nadim.bikepart.ui.theme


import com.nadim.bikepart.R
import android.content.Context
import com.microsoft.fluentui.theme.token.AliasTokens
import com.microsoft.fluentui.theme.token.IAliasTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable preference identities; labels follow the upstream demo's order. */
enum class ShopBrand(@param:androidx.annotation.StringRes val label: Int) {
    Fluent(R.string.fluent_brand), OneNote(R.string.onenote), Word(R.string.word), Excel(R.string.excel),
    PowerPoint(R.string.powerpoint), M365(R.string.m365), Teams(R.string.ms_teams);

    val aliasTokens: IAliasTokens by lazy {
        when (this) {
            Fluent -> AliasTokens(); OneNote -> OneNoteAliasTokens(); Word -> WordAliasTokens()
            Excel -> ExcelAliasTokens(); PowerPoint -> PowerPointAliasTokens()
            M365 -> M365AliasTokens(); Teams -> TeamsAliasTokens()
        }
    }
}

data class ShopThemeCustomization(val brand: ShopBrand = ShopBrand.Fluent, val accent: Boolean = true)

/** Installation-local visual preferences. Appearance remains authoritative in Shop/Room.
 * No shop writes, revisions, backup serialization, or schema changes occur here.
 */
class ShopThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("bikepart_theme", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ShopThemeCustomization(
        brand = ShopBrand.entries.firstOrNull { it.name == preferences.getString("brand", null) } ?: ShopBrand.Fluent,
        accent = preferences.getBoolean("accent", true),
    ))
    val state = mutableState.asStateFlow()

    fun setAccent(accent: Boolean) {
        preferences.edit().putBoolean("accent", accent).apply()
        mutableState.value = mutableState.value.copy(accent = accent)
    }

    fun setBrand(brand: ShopBrand) {
        preferences.edit().putString("brand", brand.name).apply()
        mutableState.value = mutableState.value.copy(brand = brand)
    }
}
