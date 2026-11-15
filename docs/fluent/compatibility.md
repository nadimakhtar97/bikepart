# Fluent compatibility gate (issue #23)

Status: **NO GO — compatibility remains unproved.** Do not start dependent migration slices until the runtime, release and accessibility checks below pass. This is an investigation, not approval for a fork or toolchain downgrade.

## Artifact selection

On 2026-11-15 Maven Central's [FluentUIAndroid 0.3.14 POM](https://repo.maven.apache.org/maven2/com/microsoft/fluentui/FluentUIAndroid/0.3.14/FluentUIAndroid-0.3.14.pom) listed `fluentui_core:0.3.11` and `fluentui_controls:0.3.3`; these independently versioned modules are pinned for the trial. The upstream [v0.3.14 build](https://github.com/microsoft/fluentui-android/blob/v0.3.14/build.gradle) used Kotlin 1.8.21 and Compose BOM 2023.09.00, whereas BikePart uses Kotlin 2.2.10 and BOM 2026.02.01. Resolution and compilation do not establish binary/runtime compatibility. Additional modules below are candidates, not yet approved dependencies.

## Coverage inventory

The following maps the current source families and states to intended replacements. Candidates and fallback token/semantic behavior must be verified before migration; none of these rows is a claim of completed coverage.

| Reachable surface and states | Source | Intended controls / fallback |
| --- | --- | --- |
| Shell, Home, Stock, Carts, More; navigation selected, loading, errors, empty and busy | `ShopApp.kt`, `ShopScreens.kt`, `ShopLayout.kt` | Fluent top bar, buttons, label and list items where suitable; Compose navigation/layout with Fluent tokens where no suitable native control exists. Preserve Android insets and Back. |
| Home summaries and status, stock health and thresholds | `ShopScreens.kt`, `StockSearch.kt` | Fluent label, card/chip and input; custom status indicator with text and semantic colors if necessary. |
| Stock search, lookup and decimal filters, menus, empty results, product rows | `StockSearch.kt`, `ShopLayout.kt` | Fluent text field, chips, menus and list items; Compose fallback for filters where selection/clearing semantics are insufficient. |
| Product detail, create/edit/copy, archive and restore, category mapping, validation | `ShopScreens.kt`, `CatalogManagement.kt`, `CategoryFields.kt` | Fluent text field, button, radio/checkbox, menu; Compose dialog/sheet fallback if required, preserving errors and confirmation. |
| Category fields, fitment, storage locations, lookup management and retirement | `CategoryFields.kt`, `FindingDetails.kt` | Fluent field, button, list and selection controls; custom Compose multi-select when needed. |
| Product photos and photo selection | `ProductPhotos.kt` | Fluent buttons and surface; preserve platform picker and Compose image/loading/error states. |
| Templates, create/edit/use/retire and custom fields | `Templates.kt` | Fluent list, fields, buttons and selection; shared Compose form fallback. |
| Scan for sale, receive or catalog; manual entry, barcode linking, camera permissions | `ContextualScanner.kt`, `ScanCamera.kt`, `BarcodeLinker.kt`, `StockArrivals.kt` | Fluent input and buttons; retain camera preview and platform permission surfaces. |
| Receiving, corrections, damage/loss and histories | `StockArrivals.kt`, `StockCorrections.kt` | Fluent fields, buttons and list; preserve validation, busy/error and historical text. |
| Label preview, copies, media, printer connection, print result | `LabelPreview.kt`, `LabelPrintControls.kt` | Fluent inputs, buttons, selection and feedback; retain rendered QR/print artwork and platform USB integration. |
| Cart list/editor, customer, quantity, product picker, paused/empty/busy/error | `Carts.kt` | Fluent fields, list, buttons and menu; Compose quantity stepper fallback with accessible actions if needed. |
| Checkout, payment and confirmation; sales history, date filter, receipt and stock history | `Sales.kt`, `SalesLog.kt` | Fluent controls and lists; evaluate calendar module for date selection; Compose dialog/date fallback if unsuitable. Preserve immutable receipt and busy states. |
| Appearance, categories and settings | `ShopScreens.kt`, `FindingDetails.kt` | Fluent radio selection, labels and list rows; System/Light/Dark persistence stays unchanged. |
| Local/Drive backup, storage selection, restore preview and confirmation | `LocalBackupControls.kt`, `DriveBackupControls.kt`, `BackupStorageControls.kt` | Fluent buttons, progress, fields, list and feedback; retain Android document picker and external sign-in. |
| Phone replacement and update checks/download/install/reminder | `PhoneReplacementControls.kt`, `UpdateControls.kt` | Fluent buttons, progress and dialog candidates; platform installer remains platform-owned. |

Fallbacks must derive colors, typography and shapes from Fluent semantic tokens, retain 48dp targets, state/error semantics and keyboard behavior, and be checked in both appearances before being accepted. The inventory is a source-based starting point; an actual navigation traversal is still required to establish completeness and inspect every conditional state.

## Evidence and remaining gates

- Test-first compilation failed on the missing trial; after implementation `:app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin` succeeded with JDK 25 and API 37 SDK. Release R8 ran, but the unreferenced trial can be removed by shrinking, so this is not a release-trial runtime check. The instrumentation interaction is not yet proven at runtime.
- Debug and release runtime reports resolve Fluent core 0.3.10 transitively to 0.3.11, icons 0.3.2, listitem 0.3.7, and upstream Compose BOM 2023.09.00 to 2026.02.01; `ui-android` resolves to 1.11.0. Resource merging succeeded in both builds; no runtime resource audit has been performed.
- No connected device was present on inspection. API 33 and current API emulator availability and runtime checks remain outstanding.
- Light/dark at 100%/200%, focused keyboard, TalkBack, focus order, contrast and release-shrunk runtime have not been inspected. No screenshots are claimed.
- Focused actual-app Room workflows, runtime dependency/resource conflict inspection, and complete control/API validation remain outstanding. The isolated trial does not replace those tests.
