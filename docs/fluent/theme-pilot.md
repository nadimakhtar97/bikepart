# Fluent theme and appearance pilot (#25)

This slice introduces the default Fluent theme for the existing shop app.
It follows [ADR 0002](../adr/0002-adopt-fluent-ui-android.md), the narrow
[compatibility GO](compatibility.md), and specification #22. The additional
brand/Accent settings requested later are tracked by #47 and the
[scope amendment](../fluent-scope-amendment.md).

## Effective configuration

`BikepartTheme` maps the existing persisted System / Light / Dark setting to
Fluent Auto / Light / Dark. It derives status/navigation icon brightness from
the effective appearance, retaining the existing stored values and Room schema.
`ShopThemeConfiguration` owns explicit scoped alias/control tokens, shared shapes,
and semantic icon substitutions. Alias typography feeds the temporary Material
bridge; all currently bundled icon painters resolve through the scoped resource
map. A later theme can provide complete alias tokens without mutating the SDK
singleton or leaving the Material bridge on an unrelated palette.

Unmigrated Material consumers receive roles derived from that same Fluent scope.
The one color adaptation is dark `surfaceContainerHighest`: native Background4
(`#333333`) with native BrandForeground1 (`#479EF5`) yields 4.498645:1, below
4.5:1 without rounding. The bridge uses native Background3 for that dark role.
No custom palette or wallpaper-derived color is introduced.

## Shared controls and fallback

The published native button accepts one string, applies single-line ellipsis,
and exposes editable-text semantics. Existing shop callers need wrapping text
and slots (including multi-line restore summaries and barcode product details).
`ShopFluentButton` is therefore a Compose fallback using the active scoped
Fluent ButtonTokens for foregrounds, fills, state borders, typography, spacing,
padding, and radius. It keeps ordinary Button semantics and a 48dp minimum
height. Pressed/focused/disabled brushes follow the native interaction state;
busy callers retain their labels and disabled click behavior. Secondary callers
retain custom colors, shapes, modifiers, selected semantics, and callbacks.

Appearance choices use one labelled, selectable 48dp radio row and a passive
native-token indicator. The row owns the sole action, selected state, disabled
state and keyboard focus. This avoids nested independent radio/row actions.

## Verification evidence

Evidence is stored under [theme-pilot](evidence/theme-pilot/), including runtime
logs, effective-color JSON and actual-app screenshots. The runtime is Android
Emulator 37.2.13, Google APIs x86_64 API37, 1080×2400 at 420dpi, with an effective 4096MB
guest (the API37 emulator raised the requested 1536MB minimum). The build uses the required JDK25 and API37 SDK without a
stack downgrade. No physical device is connected in this session.

`FluentAppearanceWorkflowTest` exercises MainActivity and the real Room-backed
Shop: persisted choices after recreation and fresh launch, explicit overrides
of opposite device brightness, Auto changes, actual rendered Fluent brand
colors, radio selected/48dp semantics, button callbacks, keyboard focus, form
validation, and a long selected barcode target at 200% in both appearances.
Only the camera transport is substituted for the barcode case, following the
existing ScanWorkflowTest seam. Existing updater and barcode workflows cover
busy/disabled/retry and reassignment callbacks without live network/camera.

The first extended run's focus assertion failed because Android was in touch
mode. The corrected check sends a physical Tab key before requesting focus;
the subsequent matrix passed. Screenshot inspection then found that the
emulator's hardware keyboard suppressed the IME. The final matrix explicitly
enables the software keyboard and asserts IME visibility before capture.
Historical failed and interrupted logs remain named separately from final logs.

`ThemeContrastExportTest` is a runtime measurement tool, not a substitute shop
workflow. It resolves both appearances from the production scope, composites
translucent backgrounds, and exports Material semantic pairs, actual shop tonal
groups, radio indicators and button rest/pressed/focused/selected/disabled colors.
Nested focus strokes are measured against their adjacent ring/background, not
against an unrelated parent behind both rings. Disabled controls are explicitly
exported as exempt. `tools/theme/validate.mjs` checks text at 4.5:1, meaningful
outlines/focus at 3:1, and rejects stale source/dependency fingerprints.

The active historical Figma generator has been retired. Reproduction commands
are in [SETUP.md](../../SETUP.md) and [theme tooling](../../tools/theme/README.md).
[Asset provenance](../../third_party/fluent-android-sources.md) records the
library and remaining Material Symbols terms; no new icon/logo assets are copied.

## Limits

This is the appearance/buttons pilot, not a completed screen migration or
production release. API33 shrunk-runtime compatibility is established by the
separate compatibility gate; this slice's actual-app evidence is API37.
Physical-device, TalkBack, Switch Access, Voice Access, Accessibility Scanner,
peripheral, genuine production-signing upgrade, foldable, and broad adaptive/
predictive-back checks were not performed in this slice. Automated semantic,
keyboard and target checks do not replace those checks. No release is published.
