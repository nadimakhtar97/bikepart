# Fluent Android demo theme implementation research

Inspected official Microsoft clone at `/tmp/bikepart-fluent-upstream`, master HEAD `7e86a0a55782e38c870be91e2f31f7cbb95fa28a`, and fetched official tag `v0.3.14` (`71be08ca96fd3c1b449920cb8d3e1c268f3dfe35`). Read-only investigation; no bikepart repository changes.

## Exact source matching the screenshot

The Accent / Appearance / Choose brand theme / Fluent Brand / OneNote / Word / Excel / PowerPoint / M365 / MS Teams popup is **`AppProperties.kt`**, not a DemoMainActivity. The Android launcher is `V2DemoListActivity` (manifest lines 14–21); this checkout has no DemoMainActivity source. `util/DemoUtils.kt` only contains layout-to-bitmap and toast helpers and does not own theming.

Pinned first-party source: [AppProperties.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/AppProperties.kt).

- `AppThemeViewModel`, lines 57–70: singleton observable state, default `FluentStyle.Brand`, brand index 0, and `ThemeMode.Auto`.
- `SetStatusBarColor`, lines 74–89: observes Accent style and selects brand or neutral status-bar background and light-status-bar flags.
- `AppBarMenu`, lines 92–187: vertical-more icon opens tokenized Fluent `Menu`; scrollable column contains the settings. Accent row and `ToggleSwitch` update `FluentStyle.Brand` / `FluentStyle.Neutral`; Appearance opens a tokenized `Dialog`; section header precedes brand choices.
- `SetAppThemeMode`, lines 191–236, and `appThemeToggle`, lines 238–247: System Default / Light / Dark radio rows; updates `FluentTheme.updateThemeMode` plus `AppCompatDelegate.setDefaultNightMode` to keep View/AppCompat UI in sync.
- `SetAppTheme`, lines 249–300: exact seven-brand order, `FluentTheme.updateAliasTokens(theme.first)` on row and radio click, and selected index update. Every radio previews its own brand's `Color80`, using a local `RadioButtonTokens.backgroundBrush` override.

Accent is a **component style choice**, not an Android dynamic-color/Material You palette switch and not a change in selected brand. Demo consumers explicitly pass the observed style into app bars/navigation components. Turning Accent off does not erase the chosen brand palette.

## Reusable theme classes and correct package boundary

Pinned source: [CustomizedAliasTokens.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/example/theme/token/CustomizedAliasTokens.kt).

These are **demo source**, package `com.example.theme.token`, not classes published by `fluentui_core`:

| Label | Class | Source start | Color80 |
| --- | --- | --- | --- |
| Fluent Brand | SDK `com.microsoft.fluentui.theme.token.AliasTokens` | core FluentAliasTokens.kt:170 | `#0F6CBD` |
| OneNote | `OneNoteAliasTokens` | CustomizedAliasTokens.kt:10 | `#7719AA` |
| Word | `WordAliasTokens` | :164 | `#185ABD` |
| Excel | `ExcelAliasTokens` | :318 | `#107C41` |
| PowerPoint | `PowerPointAliasTokens` | :472 | `#C43E1C` |
| M365 | `M365AliasTokens` | :626 | `#464FEB` |
| MS Teams | `TeamsAliasTokens` | :654 | `#5B5FC7` |

All subclass SDK `AliasTokens`. OneNote / Word / Excel / PowerPoint override `brandColor`, `brandBackgroundColor`, `brandForegroundColor`, and `brandStroke`, including brand-specific light/dark state mappings. M365 / Teams override only `brandColor` and inherit semantic mappings. **Copy/adapt the complete subclasses rather than reducing each to Color80** if matching the demo is the goal. Their extension imports are `AliasTokens`, `FluentAliasTokens`, `FluentColor`, `TokenSet` from `com.microsoft.fluentui.theme.token`, plus Compose `Color`. They can be moved under the bikepart application namespace with attribution.

[CustomizedTokens.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/CustomizedTokens.kt):20–30 supplies the menu list-item background (`Background2` / `Background2Pressed`); the remaining search-bar customizations are unrelated to matching this popup.

## Root integration and update semantics

[FluentTheme.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/fluentui_core/src/main/java/com/microsoft/fluentui/theme/FluentTheme.kt):

- `ThemeMode` at :10 exposes `Light`, `Dark`, `Auto`.
- Composable `FluentTheme(aliasTokens: IAliasTokens? = null, controlTokens: IControlTokens? = null, themeMode: ThemeMode? = null)` at :39 observes SDK singleton LiveData and provides composition locals.
- Explicit `aliasTokens` / `themeMode` values take precedence over singleton updates; choose one ownership model deliberately. A root with fixed explicit alias tokens would prevent `updateAliasTokens` from changing its descendants.
- `FluentTheme.updateAliasTokens` :102, `updateThemeMode` :118 update observed values and increment theme ID.
- Semantic colors are `FluentColor` light/dark pairs, resolved using `.value(themeMode)` or `.value()` inside the theme scope.

[V2DemoActivity.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/V2DemoActivity.kt):188–250 wraps content in default `FluentTheme`, invokes `SetStatusBarColor`, passes `AppThemeViewModel.appThemeStyle.value` into its Fluent app bar, invokes `AppBarMenu` in the menu slot, and reads token backgrounds/foregrounds. [V2DemoListActivity.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/V2DemoListActivity.kt):320 onwards follows the same model and passes style to navigation components.

The demo's singleton `ViewModel`/LiveData state is in-memory; the inspected file does not implement persistence across process death. Its Accent row also has local `remember { true }` state separate from the checked value. For application integration, use one app-owned settings state for brand/style/appearance and save preferences using the project's existing persistence convention; this is an application design recommendation, not an upstream requirement.

## v0.3.14 versus master

`git diff v0.3.14 HEAD --` returned **no differences** for all directly relevant files checked:

- `FluentUI.Demo/.../AppProperties.kt`
- `FluentUI.Demo/.../com/example/theme/token/CustomizedAliasTokens.kt`
- `fluentui_core/.../theme/FluentTheme.kt`
- `fluentui_core/.../theme/token/FluentAliasTokens.kt`
- `FluentUI.Demo/.../V2DemoActivity.kt`
- `FluentUI.Demo/.../V2DemoListActivity.kt`

Therefore the matching screenshot feature and core theme APIs are present in pinned `v0.3.14`, with no relevant source drift in these files at the supplied master commit. This does not assert all upstream modules are identical or prove binary/runtime integration in bikepart; compile and UI checks remain necessary later.

## License / attribution

[Official MIT LICENSE](https://github.com/microsoft/fluentui-android/blob/v0.3.14/LICENSE) grants copying and modification and requires preserving Microsoft's copyright and permission notice in copies or substantial portions. When copying the demo alias token classes or adapting a substantial portion of AppProperties, retain the full MIT notice in an appropriate third-party notice/license artifact and add a source attribution comment with pinned version/path. No upstream demo app icon/logo assets are needed to implement this theme feature.
