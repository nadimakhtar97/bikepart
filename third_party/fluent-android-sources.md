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

No demo brand classes, application logos, or new icon assets are copied in this
pilot. Existing Google Material Symbols retain their separate Apache 2.0 license
and [source record](material-symbols-sources.md). Their semantic resource IDs
are resolved through a scoped configuration map so a later family can replace
them without changing screen callers. Fluent icon availability and asset terms
must be checked separately before adopting those assets.

The [scope amendment](../docs/fluent-scope-amendment.md) and issue #47 track the
later demo brand/Accent settings; they are separate from this default-theme pilot.
