# Now in Android adoption — validation

Validated 2026-11-11 against the user's Figma file
`MapX1cK2WjBKsPS0ZDLIbw`. The [UI guidelines](ui-guidelines.md) identify the exact
nodes and deliberate shop adaptations. This replaces the original teal visual
decision, while preserving shop behavior and its persisted appearance setting.

## Implementation

The reference's current light/dark variables and applied typography are recorded
in `tools/theme/figma-tokens.json` and `ui/theme/ShopTokens.kt`. Generated Material
roles, centered app bars, surface navigation, neutral main actions, rounded
sections, category chips, stock rows, detail groups and appearance rows reuse
Compose Material 3. Existing local 24dp Material Symbols remain the shop icons.
News images, account controls and a separate Android-brand selector are outside
this adaptation. The adopted variant is the file's Default theme.

Domain code, database schema and persistence APIs are unchanged. No migration is
needed. Appearance still commits through Room and survives reopening.

## Automated checks

```
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.nadim.bikepart.ShopWorkflowTest' --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain
node tools/theme/validate.mjs
```

All passed. The full suite ran offline on the API 37 portrait-phone emulator:
one JVM test and nine instrumentation tests. Existing checks cover creation,
editing, fresh activity/ViewModel/Room startup, retained SKU, input validation,
opening reconciliation, numeric limits, transaction rollback and persisted
settings. The UI workflow now uses a long product title and price
`9223372036854775807`, verifies visible Indian grouping
`₹92,23,37,20,36,85,47,75,807`, and checks the reopened value.

Light and Dark UI checks run at 200% system text size with labelled controls,
48dp primary targets, selection semantics and announced errors. All reference
roles match the snapshot; 94 normal-text/essential-outline contrast checks pass,
including the 8% tonal section background. Lowest checked normal-text contrast:
4.55:1. Lint reports 0 errors and 11 existing starter/version/resource warnings.

## Visual inspection

Actual emulator screenshots were compared with the inspected Figma typography,
phone and settings examples. Home, forms and appearance screens were inspected
at normal and 200% text in light/dark modes. Cards and chips wrap/grow, fields
and page content scroll, main actions remain reachable, navigation labels stay
visible, and system-bar icons follow app brightness. Local captures are in
`.scratch/nia-*.png`; they are validation artifacts, not runtime UI assets.

This is an adaptation to shop content rather than a duplicate of the reference
news application. Manual TalkBack traversal and physical-phone validation have
not been performed.

## Standards

Reviewed `847f219...3bcbb96` against `AGENTS.md`, current UI guidelines, glossary
and schema guidance. No actionable Standards findings. The review confirmed
central token ownership, native component reuse, appearance persistence and
shared screen patterns. No new meaningful baseline smells were reported.

## Spec

No actionable Spec findings. The review confirmed reference Default colors and
typography, persisted brightness modes, shop-specific navigation/content and
unchanged persistence operations. An optional consistency cleanup explicitly
disables the distinct-product checkbox during saving; the full suite includes
that cleanup.

Review totals: Standards 0 actionable findings; Spec 0 actionable findings.
