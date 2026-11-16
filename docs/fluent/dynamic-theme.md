# Demo brand and Accent settings (#47)

Work in progress on the completed #25 pilot. Source research and exact revisions
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
singleton changes or keyed subtree resets are introduced. Actual rendered
color changes and unfinished input preservation still require runtime evidence.

Final runtime/effective-contrast evidence and limitations will be recorded here
when checks finish. No release is published by this slice.

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
on Background3. Only its transitional Material link role uses its own native
Color110 ramp step. Complete copied alias classes remain untouched. Word's
native secondary border is 2.79:1 on its selected barcode container; custom-fill
secondary button boundaries use native StrokeAccessible while ordinary native
button states and nested focus rings remain intact. These are measured role
adaptations, not a new palette engine.

Accent changes Brand/Neutral navigation and native AppBar styling. Native controls
without a FluentStyle parameter retain their selected brand palette, matching the
demo's distinction between component style and brand choice. System bar icons
are chosen from actual rendered backgrounds (including animated branded headers),
with the Back glyph inheriting the app bar's navigation foreground.
