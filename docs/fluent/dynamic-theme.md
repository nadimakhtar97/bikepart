# Demo brand and Accent settings (#47)

Implemented on the completed #25 pilot. Source research and exact revisions
are in [demo-source.md](demo-source.md); the accepted customization amendment is
[fluent-scope-amendment.md](../fluent-scope-amendment.md).

Seven complete upstream alias themes are offered in demo order. Brand and Accent
are installation-local SharedPreferences settings owned by the app view model;
they never write Room, advance the shop revision, or enter shop backup formats.
The existing System / Light / Dark field remains authoritative through
`Shop.setAppearance`, including its existing backup/restore contract.

The root receives selected alias tokens explicitly. Native radio/switch token
methods in core0.3.11 and controls0.3.3 read scoped aliases on composition; their
`themeID` reads trigger recomposition and do not memoize colors. No global theme
singleton changes or keyed subtree resets are introduced. Actual rendered native switch recoloring and unfinished input/navigation
preservation are verified below.

The checks below establish this theme-settings slice. The broader Fluent migration
and production-release gates remain in force; this slice publishes no release.

## Measured accessibility adaptations

The first 28-scope runtime export is retained as `fluent-contrast-initial.json`
and its validation failure log. Actual native unchecked ToggleSwitch geometry has
no independent outline; its track is only 1.01–1.83:1 on actual group parents and
its light knob/track is 1.14:1. A captured unchecked More switch confirms the
barely visible track. Selected dark white knobs also fail on four brands
(2.08–2.84:1). Scoped `ShopControlTokens` retains native geometry/interactions,
selected tracks, and disabled colors, substitutes native StrokeAccessible for
enabled unchecked tracks and native ForegroundOnColor for enabled knobs.

M365's inherited dark Color100 link measures 4.10:1 on Background2 and 3.62:1
on Background3. Its scoped BrandForeground1 dark role uses its own native Color110 ramp step
for both native controls and the Material bridge. Complete copied alias classes remain untouched. Word's
native secondary border is 2.79:1 on its selected barcode container; native StrokeAccessible also measures only 2.67:1 there, so custom-fill
secondary button boundaries use native StrokeFocus2 while ordinary native
button states and nested focus rings remain intact. These are measured role
adaptations, not a new palette engine.

Accent changes Brand/Neutral navigation and native AppBar styling. Native controls
without a FluentStyle parameter retain their selected brand palette, matching the
demo's distinction between component style and brand choice. System bar icons
are chosen from actual rendered backgrounds (including animated branded headers),
with the Back glyph inheriting the app bar's navigation foreground.

## Scroll viewport review

The 200% Excel/M365/PowerPoint captures show an earlier heading/subtitle partially
clipped when selecting lower rows. Full original inspection and the unchanged
shell viewport source confirm ordinary clipping at the scroll viewport's y=63
edge. `CheckStatusRegion.java` and `logs/status-region-review.txt` find zero app
text pixels in a representative status-bar region that excludes system symbols.
This is not an inset regression; no shell/router change was made for it.

## Final verification — 2026-11-16

The actual runtime is the assigned Google APIs x86_64 Android Emulator API37,
1080×2400 at 420dpi (about 411dp wide), with JDK25/API37 SDK and the existing
pinned Fluent core0.3.11/controls0.3.3 dependencies. No dependency or toolchain
downgrade was introduced. Original System appearance, Fluent Brand, Accent on,
font scale, device night setting and debug APK were restored after verification.

| Check | Result |
| --- | --- |
| Debug and instrumentation APKs | Built |
| Shrunk release / R8 | Built; installed artifact hash and non-debuggable flags verified on API37 |
| Unit tests | 30 passed; zero failures/errors/skips |
| Android lint | Zero errors; 209 warnings remain |
| Actual MainActivity + real Room | Six focused checks passed across bounded runs |
| Effective runtime contrast | 2,212 pairs passed across exactly 28 brand/style/appearance scopes; fingerprint current |
| Validator rejection checks | Stale source, missing scope, duplicated scope and below-threshold colors rejected |
| APK license notice | Full MIT asset in debug and shrunk APKs equals the recorded source notice |
| Cold process reopening | Shrunk app retained M365 + Accent off + Light after actual force-stop/relaunch |

The six actual-app checks cover selection after recreation/fresh launch, visible
Accent navigation changes, immediate native Excel/OneNote switch recoloring,
one labelled 48dp switch action for keyboard and native hit testing, retained
selected brand with Accent off, retained stored appearance and real seeded
products/stock/unfinished cart/immutable sale/revision, retained unfinished input
and navigation through theme changes/recreation, all-brand rendering and native
header/system-bar checks at normal/double text, and a long selected Word barcode
target with visible boundary and working Cancel callback in both appearances.
The barcode check substitutes only the existing approved camera transport.

The first combined run passed four behaviors but Android's
`UiAutomation.takeScreenshot()` returned null during repeated captures. That
historical failure remains recorded. Only the rendering test was retried, using
binary `screencap -p` descriptors with decoded-PNG size validation; it passed.
The English-identical extraction of the two generic labels to string resources
required a fresh contrast fingerprint, which was regenerated and passed.

The shrunk-runtime check used the disposable local debug key, verified the actual
installed APK SHA256 and absence of DEBUGGABLE, selected M365/Accent off/Light,
force-stopped and relaunched, checked durable selected states, switched to
Dark/Accent on, and opened New product. It restored the original settings and
debug APK. This is not a production-signed upgrade or release.

Evidence: [build/R8/lint/unit](evidence/dynamic-theme/logs/final-build-release-lint-unit.txt),
[artifact counts/license checks](evidence/dynamic-theme/logs/artifact-checks.txt),
[initial combined actual-app run](evidence/dynamic-theme/logs/full-runtime.txt),
[rendering-only retry](evidence/dynamic-theme/logs/rendering-runtime.txt),
[final contrast and Word boundary/callback](evidence/dynamic-theme/logs/resource-refresh-word-runtime.txt),
[validator rejection checks](evidence/dynamic-theme/logs/validator-rejections.txt),
[shrunk cold-reopen smoke](evidence/dynamic-theme/logs/release-smoke.txt), and
[effective color export](evidence/dynamic-theme/fluent-contrast.json).

## Actual-app screenshots

All original PNGs remain unchanged. Reviewed contact sheets show the seven brand
choices, selected state, wrapping, adapted switch tracks/knobs, Accent navigation
effect and header/system-bar foregrounds:
[page 1](evidence/dynamic-theme/overview-1.png),
[page 2](evidence/dynamic-theme/overview-2.png),
[page 3](evidence/dynamic-theme/overview-3.png),
[page 4](evidence/dynamic-theme/overview-4.png).

| Brand | Light 100% | Dark 100% | Light 200% | Dark 200% |
| --- | --- | --- | --- | --- |
| Fluent Brand | [image](evidence/dynamic-theme/screenshots/dynamic-fluent-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-fluent-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-fluent-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-fluent-dark-2.0.png) |
| OneNote | [image](evidence/dynamic-theme/screenshots/dynamic-onenote-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-onenote-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-onenote-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-onenote-dark-2.0.png) |
| Word | [image](evidence/dynamic-theme/screenshots/dynamic-word-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-word-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-word-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-word-dark-2.0.png) |
| Excel | [image](evidence/dynamic-theme/screenshots/dynamic-excel-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-excel-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-excel-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-excel-dark-2.0.png) |
| PowerPoint | [image](evidence/dynamic-theme/screenshots/dynamic-powerpoint-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-powerpoint-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-powerpoint-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-powerpoint-dark-2.0.png) |
| M365 | [image](evidence/dynamic-theme/screenshots/dynamic-m365-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-m365-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-m365-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-m365-dark-2.0.png) |
| MS Teams | [image](evidence/dynamic-theme/screenshots/dynamic-teams-light-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-teams-dark-1.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-teams-light-2.0.png) | [image](evidence/dynamic-theme/screenshots/dynamic-teams-dark-2.0.png) |

Representative 200% Accent controls: [light Brand](evidence/dynamic-theme/screenshots/dynamic-controls-brand-light-2.0.png),
[light Neutral](evidence/dynamic-theme/screenshots/dynamic-controls-neutral-light-2.0.png),
[dark Brand](evidence/dynamic-theme/screenshots/dynamic-controls-brand-dark-2.0.png),
[dark Neutral](evidence/dynamic-theme/screenshots/dynamic-controls-neutral-dark-2.0.png).
Native headers: [light Brand](evidence/dynamic-theme/screenshots/dynamic-header-brand-light-2.0.png),
[light Neutral](evidence/dynamic-theme/screenshots/dynamic-header-neutral-light-2.0.png),
[dark Brand](evidence/dynamic-theme/screenshots/dynamic-header-brand-dark-2.0.png),
[dark Neutral](evidence/dynamic-theme/screenshots/dynamic-header-neutral-dark-2.0.png).
Word selected boundary at 200%: [light](evidence/dynamic-theme/screenshots/dynamic-word-selected-light-2.0.png),
[dark](evidence/dynamic-theme/screenshots/dynamic-word-selected-dark-2.0.png).
Actual shrunk app after cold reopening: [M365/Neutral/Light](evidence/dynamic-theme/screenshots/release-m365-light-neutral-after-cold.png),
[M365/Brand/Dark](evidence/dynamic-theme/screenshots/release-m365-dark-brand.png),
[native dark header](evidence/dynamic-theme/screenshots/release-m365-dark-header.png).

## Limits and downstream integration

Physical devices, TalkBack, Voice Access, Switch Access, Accessibility Scanner,
360dp/larger/adaptive windows, predictive-back/foldable cases and a genuine
production-signing upgrade were not exercised in this slice. API33 shrunk-runtime
compatibility remains the separate original gate; these new settings were
exercised on API37. Semantic state/focus/target checks and numeric contrast do
not replace those broader checks. No UI inspection ran alongside instrumentation.

This export measures the currently integrated pilot components and actual
8% brand-tinted group parent. The independent form/collection owners add their
runtime recorders and the production group helper when merged. Their final
exports must resolve that combined configuration and refresh the source
fingerprint; this slice does not claim unmerged control families are verified.
