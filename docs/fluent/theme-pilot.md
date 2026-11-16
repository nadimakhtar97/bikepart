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
The first accessibility adaptation is dark `surfaceContainerHighest`: native Background4
(`#333333`) with native BrandForeground1 (`#479EF5`) yields 4.498645:1, below
4.5:1 without rounding. The bridge uses native Background3 for that dark role.
The second adaptation maps Material `outlineVariant` to the same native
StrokeAccessible token as `outline`. Unmigrated Material FilterChip boundaries
otherwise use Stroke1 (`#D1D1D1` light / `#4D4D4D` dark): only 1.36:1 / 2.32:1
on the actual tinted ShopGroup parents. The exporter checks those parent pairs
and all bridge surfaces. Native shared-button rest/pressed brand outlines
already pass; they are preserved, including custom BarcodeLinker colors and
nested focus rings. Later form/chip owners must retain the verified boundary
contrast when replacing the Material bridge.
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

## Final result — 2026-11-16

| Check | Result |
| --- | --- |
| Debug app and instrumentation APKs | Built |
| Shrunk release APK / R8 | Built; debug-key-signed copy exercised only on the disposable emulator |
| Android lint | Passed, zero errors; 207 warnings remain (mostly unused resources and existing dependency/style checks) |
| Unit tests | 30 passed, zero failures/errors |
| Actual-app focused instrumentation | 7 passed; corrected measurement-only rerun also passed |
| Effective Fluent/bridge contrast | 118 pairs passed with current source fingerprint; stale exports and below-threshold colors are rejected |
| Actual shrunk-release smoke | Launch, More, Light/Dark, original System restoration, navigation, shared action and required-title validation passed |

The release smoke used the local disposable debug signing key, preserved the
emulator's shop records, restored the original System appearance, and restored
the debug APK afterwards. It is not a production-signed upgrade or release.

Final logs: [build/lint/unit](evidence/theme-pilot/logs/final-build-lint-unit.txt),
[actual-app run](evidence/theme-pilot/logs/final-runtime.txt),
[contrast](evidence/theme-pilot/logs/contrast.txt),
[rejection checks](evidence/theme-pilot/logs/tooling-rejection-checks.txt), and
[release smoke](evidence/theme-pilot/logs/release-smoke-ui.txt).

Actual-app renderings were visually inspected for readable wrapping, selected
state, focus rings, system-bar brightness and focused content above the IME:

| State | Light 100% | Dark 100% | Light 200% | Dark 200% |
| --- | --- | --- | --- | --- |
| Appearance | [image](evidence/theme-pilot/screenshots/pilot-more-light-1.0.png) | [image](evidence/theme-pilot/screenshots/pilot-more-dark-1.0.png) | [image](evidence/theme-pilot/screenshots/pilot-more-light-2.0.png) | [image](evidence/theme-pilot/screenshots/pilot-more-dark-2.0.png) |
| Focused primary action | [image](evidence/theme-pilot/screenshots/pilot-button-focus-light-1.0.png) | [image](evidence/theme-pilot/screenshots/pilot-button-focus-dark-1.0.png) | [image](evidence/theme-pilot/screenshots/pilot-button-focus-light-2.0.png) | [image](evidence/theme-pilot/screenshots/pilot-button-focus-dark-2.0.png) |
| Focused field with visible IME | [image](evidence/theme-pilot/screenshots/pilot-keyboard-light-1.0.png) | [image](evidence/theme-pilot/screenshots/pilot-keyboard-dark-1.0.png) | [image](evidence/theme-pilot/screenshots/pilot-keyboard-light-2.0.png) | [image](evidence/theme-pilot/screenshots/pilot-keyboard-dark-2.0.png) |

Long selected secondary slot: [light 200%](evidence/theme-pilot/screenshots/pilot-long-selected-light-2.0.png),
[dark 200%](evidence/theme-pilot/screenshots/pilot-long-selected-dark-2.0.png).
Independent shrunk release: [light](evidence/theme-pilot/screenshots/release-more-light.png),
[dark](evidence/theme-pilot/screenshots/release-more-dark.png),
[required-title validation](evidence/theme-pilot/screenshots/release-validation.png).
