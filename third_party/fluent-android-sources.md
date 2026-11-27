# Fluent Android visual source

BikePart pins Microsoft Fluent UI Android `fluentui_core:0.3.11` and
`fluentui_controls:0.3.3`, selected from the official 0.3.14 release set.
The published runtime tokens are the source for theme, typography, corner
radii, appearance indicator, and shared button styling. The wrappers consume
public APIs; they do not copy a separately maintained palette.

Upstream: https://github.com/microsoft/fluentui-android/tree/v0.3.14
Code license: https://github.com/microsoft/fluentui-android/blob/v0.3.14/LICENSE
(MIT, copyright Microsoft Corporation).

The fallback adapts native button tokens to wrapping Compose slots and ordinary
button semantics because the published string-only button truncates to one line
and exposes editable-text semantics. Native state brushes, text, borders, padding,
typography, and corner radius remain authoritative. Android's 48dp minimum target
and documented Material-bridge text/boundary contrast adaptations are applied.

The initial #25 pilot copied no demo brand classes, application logos, or new
icon assets. #47 adds the attributed demo alias classes recorded below. Existing Google Material Symbols retain their separate Apache 2.0 license
and [source record](material-symbols-sources.md). Their semantic resource IDs
are resolved through a scoped configuration map so a later family can replace
them without changing screen callers. Fluent icon availability and asset terms
must be checked separately before adopting those assets.

The [scope amendment](../docs/fluent-scope-amendment.md) and issue #47 track the
demo brand/Accent extension to the initial default-theme pilot.

## Demo brand themes (#47)

`ui/theme/DemoBrandAliasTokens.kt` copies all six complete alias subclasses
from `FluentUI.Demo/src/main/java/com/example/theme/token/CustomizedAliasTokens.kt`
at official tag `v0.3.14`, commit `71be08ca96fd3c1b449920cb8d3e1c268f3dfe35`.
Only the application package/header is adapted. Complete semantic overrides and
brand ramps remain upstream definitions; Fluent Brand uses SDK `AliasTokens`.
The full required Microsoft copyright and MIT permission notice is preserved in
[fluentui-android-MIT.txt](fluentui-android-MIT.txt) and packaged unchanged in
`app/src/main/assets/third_party/fluentui-android-MIT.txt`. No demo logos are copied.
The matching control behavior and version comparison are recorded in
[demo source research](../docs/fluent/demo-source.md).

## Fluent Scaffold

The app vendors `fluentui_core/src/main/java/com/microsoft/fluentui/compose/Scaffold.kt`
from the upstream `master` tree at commit
`95205e33c8067d922bb45bd07490acc18c50755f`, preserving its Apache-2.0 source
header. The file is included at
`app/src/main/java/com/microsoft/fluentui/compose/Scaffold.kt`; the complete
license text is retained in `third_party/scaffold-android-APACHE-2.0.txt` and
packaged at `app/src/main/assets/third_party/scaffold-android-APACHE-2.0.txt`.

The pinned published Fluent artifacts do not expose the Scaffold demonstrated
by `FluentUI.Demo/.../V2ScaffoldActivity.kt`. To use that API without changing
the published dependency set, each route calls the vendored scaffold directly.
The app shell remains responsible for primary navigation only. See
[ADR 0003](../docs/adr/0003-per-route-fluent-scaffolds.md).

## Fluent SearchBar

The shared shop search field adapts [Microsoft's tokenized SearchBar](https://github.com/microsoft/fluentui-android/blob/95205e33c8067d922bb45bd07490acc18c50755f/fluentui_topappbars/src/main/java/com/microsoft/fluentui/tokenized/SearchBar.kt)
under the [MIT license](fluentui-android-MIT.txt). It uses Fluent's search tokens,
icons, and focus/clear interactions. Screens own the query so they can restore
it, clear it programmatically, and seed Stock from Home. The adaptation omits
the people picker and microphone and applies 48dp action targets. The published
Fluent dependencies retain their existing pins.
