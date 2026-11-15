# Fluent demo research and dynamic theme scope

On 2026-11-15 the project owner expanded issue #22 to include the supplied
FluentUI Demo 0.3.14 APK as a component reference and the demo's dynamic theme
controls. This instruction supersedes the original exclusion of customization
for this feature. [Issue #47](https://github.com/nadimakhtar97/bikepart/issues/47)
tracks the implementation after the shared Fluent theme pilot (#25).

The requested controls are Accent, Appearance, and brand selection: Fluent
Brand, OneNote, Word, Excel, PowerPoint, M365, and MS Teams. Existing persisted
System / Light / Dark values and shop-data contracts remain authoritative.

The APK and upstream source are research references. Component screenshots
must distinguish legacy View controls from Compose controls, identify the
captured state and API, and record failures. Demo screenshots do not establish
BikePart's runtime, accessibility, workflow, or release readiness.

Copied upstream implementation must retain its license attribution and source
revision. Theme choices must feed the centralized Fluent configuration and
temporary Material mappings, and must undergo rendered contrast and usability
checks in every supported appearance.
