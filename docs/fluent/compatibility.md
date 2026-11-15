# Fluent compatibility gate (issue #23)

Status: **GO for the scoped theme/appearance/shared-button pilot.** The final
minimal harness passes debug and shrunk-release runtime on minimum API33 and
current API37 using supported Fluent tokens and accessibility adapters. This is
not full-screen migration or production-release approval. The final verdict and
matrix below supersede the historical investigation checkpoints.

## Artifact selection

On 2026-11-15 Maven Central's [FluentUIAndroid 0.3.14 POM](https://repo.maven.apache.org/maven2/com/microsoft/fluentui/FluentUIAndroid/0.3.14/FluentUIAndroid-0.3.14.pom) listed `fluentui_core:0.3.11` and `fluentui_controls:0.3.3`; these independently versioned modules are pinned for the trial. The upstream [v0.3.14 build](https://github.com/microsoft/fluentui-android/blob/v0.3.14/build.gradle) used Kotlin 1.8.21 and Compose BOM 2023.09.00, whereas BikePart uses Kotlin 2.2.10 and BOM 2026.02.01. Resolution and compilation do not establish binary/runtime compatibility. Additional modules below are candidates, not yet approved dependencies.

## Coverage inventory

The following maps the current source families and states to intended replacements. Candidates and fallback token/semantic behavior must be verified before migration; none of these rows is a claim of completed coverage.

| Reachable surface and states | Source | Intended controls / fallback |
| --- | --- | --- |
| Shell, Home, Stock, Carts, More; navigation selected, loading, errors, empty and busy | `ShopApp.kt`, `HomeScreen.kt`, `MoreScreen.kt`, `StockScreens.kt`, `ShopCollections.kt` | Fluent top bar, buttons, label and list items where suitable; Compose navigation/layout with Fluent tokens where no suitable native control exists. Preserve Android insets and Back. |
| Home summaries and status, stock health and thresholds | `HomeScreen.kt`, `StockSearch.kt`, `StockThreshold.kt` | Fluent label, card/chip and input; custom status indicator with text and semantic colors if necessary. |
| Stock search, lookup and decimal filters, menus, empty results, product rows | `StockSearch.kt`, `ShopCollections.kt` | Fluent text field, chips, menus and list items; Compose fallback for filters where selection/clearing semantics are insufficient. |
| Product detail, create/edit/copy, archive and restore, category mapping, validation | `ProductDetails.kt`, `ProductForm.kt`, `ArchivedProducts.kt`, `CatalogManagement.kt`, `CategoryFields.kt` | Fluent text field, button, radio/checkbox, menu; Compose dialog/sheet fallback if required, preserving errors and confirmation. |
| Category fields, fitment, storage locations, lookup management and retirement | `CategoryFields.kt`, `FindingDetails.kt` | Fluent field, button, list and selection controls; custom Compose multi-select when needed. |
| Product photos and photo selection | `ProductPhotos.kt` | Fluent buttons and surface; preserve platform picker and Compose image/loading/error states. |
| Templates, create/edit/use/retire and custom fields | `Templates.kt` | Fluent list, fields, buttons and selection; shared Compose form fallback. |
| Scan for sale, receive or catalog; manual entry, barcode linking, camera permissions | `ContextualScanner.kt`, `ScanCamera.kt`, `BarcodeLinker.kt`, `StockArrivals.kt` | Fluent input and buttons; retain camera preview and platform permission surfaces. |
| Receiving, corrections, damage/loss and histories | `StockArrivals.kt`, `StockCorrections.kt` | Fluent fields, buttons and list; preserve validation, busy/error and historical text. |
| Label preview, copies, media, printer connection, print result | `LabelPreview.kt`, `LabelPrintControls.kt` | Fluent inputs, buttons, selection and feedback; retain rendered QR/print artwork and platform USB integration. |
| Cart list/editor, customer, quantity, product picker, paused/empty/busy/error | `Carts.kt` | Fluent fields, list, buttons and menu; Compose quantity stepper fallback with accessible actions if needed. |
| Checkout, payment and confirmation; sales history, date filter, receipt and stock history | `Checkout.kt`, `SaleReceipt.kt`, `SalesHistory.kt`, `SalesLog.kt` | Fluent controls and lists; evaluate calendar module for date selection; Compose dialog/date fallback if unsuitable. Preserve immutable receipt and busy states. |
| Appearance, categories and settings | `MoreScreen.kt`, `CategorySettings.kt`, `FindingDetails.kt` | Fluent radio selection, labels and list rows; System/Light/Dark persistence stays unchanged. |
| Local/Drive backup, storage selection, restore preview and confirmation | `LocalBackupControls.kt`, `DriveBackupControls.kt`, `BackupStorageControls.kt` | Fluent buttons, progress, fields, list and feedback; retain Android document picker and external sign-in. |
| Phone replacement and update checks/download/install/reminder | `PhoneReplacementControls.kt`, `UpdateControls.kt` | Fluent buttons, progress and dialog candidates; platform installer remains platform-owned. |

Fallbacks must derive colors, typography and shapes from Fluent semantic tokens, retain 48dp targets, state/error semantics and keyboard behavior, and be checked in both appearances before being accepted. The inventory is a source-based starting point; an actual navigation traversal is still required to establish completeness and inspect every conditional state.

## Evidence and remaining gates

- Test-first compilation failed on the missing trial; after implementation `:app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin` succeeded with JDK 25 and API 37 SDK. Release R8 ran with a shell-accessible trial activity retained in the manifest; this is not a release-trial runtime check. The instrumentation interaction is not yet proven at runtime.
- Debug and release runtime reports resolve Fluent core 0.3.10 transitively to 0.3.11, icons 0.3.2, listitem 0.3.7, and upstream Compose BOM 2023.09.00 to 2026.02.01; `ui-android` resolves to 1.11.0. Resource merging succeeded in both builds; no runtime resource audit has been performed.
- No device was initially connected. Android CLI installed the emulator and API 33 and 37 Google APIs x86_64 images. `/dev/kvm` was inaccessible to this user; a temporary API 33 emulator (`emulator-5554`) booted using `-accel off` after approximately 14 minutes. The initial install attempted before boot completion failed in Android storage initialization; after boot, the debug APK install did not complete within approximately three minutes and the waiting client was interrupted. No instrumentation ran. The API 37 image was not booted. These are host/runtime limitations, not evidence of a Fluent incompatibility.
- Light/dark at 100%/200%, focused keyboard, TalkBack, focus order, contrast and release-shrunk runtime have not been inspected. No screenshots are claimed.
- Focused actual-app Room workflows, runtime dependency/resource conflict inspection, and complete control/API validation remain outstanding. The isolated trial does not replace those tests.

## API 33 release verification — 2026-11-15

This section supersedes the earlier emulator-status bullets above. After enabling KVM, the Compose `runtime-livedata` adapter was added using the app's BOM. `FluentCompatibilityTest` passed on API 33 in debug. `:app:assembleRelease` passed with R8 and release lint; the unsigned release was signed with the local debug key for this disposable emulator. It installed and launched, accepted `BrakePad` in the trial input and displayed `Applied: BrakePad` after Apply. This is not a production-signed upgrade test.

Device: API 33 Google APIs x86_64, emulator 37.2.13, 1080×2400, 420 dpi, KVM. Captured and visually inspected [light 100%](evidence/api33/light-100.png), [light 200%](evidence/api33/light-200.png), [dark 100%](evidence/api33/dark-100.png), [dark 200%](evidence/api33/dark-200.png), and [dark 200% with keyboard](evidence/api33/dark-200-keyboard.png). The trial is an isolated probe, not the migrated shop app.

**Visual/accessibility result: FAIL.** In both dark captures the trial labels use nearly white text on a light background and are effectively unreadable. Labels are unusually large even at 100%; the trial's layout has not demonstrated usable scaling. At 200% with the keyboard open the focused field is visible, but Apply is hidden beneath the keyboard. At 100%, the accessibility tree reported the clickable Apply node as `android.widget.EditText` with bounds 155×110 pixels (approximately 59×42 dp at 420 dpi), below the 48 dp height requirement. The input exposes a `Trial input` description and editable action. These findings describe the trial's integration of Fluent, not a determination of an upstream library defect. TalkBack, full reading order, contrast measurements, API 37, and physical-device checks were not performed.

The gate remains NO GO. Investigate theme surface/background, typography selection and button semantics/target sizing; rerun visual and accessibility checks before approving migration. Full screen/state inventory and actual-app workflow verification remain outstanding.

## Physical device investigation — 2026-11-15

A Samsung SM-S911B (Android 17, API 37, 1080×2340, 480 dpi) ran an isolated **debug** build with a different application ID to preserve the existing BikePart installation. The device reported system night mode enabled and font scale 1.0, but the trial activity inherits `android:Theme.Material.Light.NoActionBar`; this was not a valid dark-theme check.

Manual entry of `BrakePad` and Apply produced `Applied: BrakePad`. The screen shows oversized labels and content drawn under the status bar. The keyboard obscures Apply at 100%. The accessibility hierarchy identifies the clickable Apply node as `android.widget.EditText`, with bounds 180×126 pixels, or 60×42 dp; its height is below 48 dp. These are observations of this trial integration, not proof of an upstream defect.

The isolated debug instrumentation test failed with `No compose hierarchies found in the app`; its cause remains undetermined, so this is not evidence of a Fluent runtime incompatibility. A 200% attempt was interrupted by the phone locking and produced no usable evidence. Font scale was restored to 1.0 and the temporary trial and test packages were removed. Release-shrunk execution, valid light/dark comparisons, 200% scaling, TalkBack, reading order, measured contrast, and actual-app Room workflows remain unverified on this phone. The gate remains **NO GO**.

## Second physical device investigation — 2026-11-15

A V2250 (Android 15, API 35, 1260×2800, 480 dpi) ran the same isolated debug trial under a separate application ID. `FluentCompatibilityTest` passed (1 test), including entry and Apply. This does not explain the earlier API 37 phone's instrumentation failure.

With system night mode enabled, the trial's labels were nearly white on a light background and effectively unreadable at both 100% and 200% font scale. With system light mode enabled, labels were readable at both scales, but the heading remained unusually large and overlapped the status bar. At 100%, the keyboard covered Apply. The 100% accessibility hierarchy exposed the clickable Apply node as `android.widget.EditText`, with bounds 180×126 pixels (60×42 dp at 480 dpi), below the 48 dp height requirement. The trial uses a light platform activity theme alongside Fluent's system-dependent colors; these findings concern that integration and do not establish an upstream defect.

The device's original dark mode and 1.0 font scale were restored, and the temporary packages were removed. This was a debug probe; release-shrunk runtime, TalkBack, measured contrast, reading order, and actual-app workflows remain unverified. The gate remains **NO GO**.

### Dark-background hypothesis check

On the V2250 in system dark mode at 100%, a temporary debug variant changed only the trial's `Column` to fill the screen and use `FluentTheme.aliasTokens.neutralBackgroundColor[FluentAliasTokens.NeutralBackgroundColorTokens.CanvasBackground].value()` as its background. The labels appeared white on a dark canvas and were visually readable, confirming the missing matching background caused the observed label contrast failure in this trial. The unusually large heading and status-bar overlap persisted. This was an isolated experiment, not a committed fix or a measured contrast/accessibility pass; other gates remain open.


## Corrected integration trial — 2026-11-15

The trial now supplies a matching Fluent `CanvasBackground`, explicit `Title2` and
`Body1` labels (the upstream Label default is `Display`), edge-to-edge safe drawing
insets, IME resizing and a pinned Apply action outside scrollable content. Input
and applied values survive activity recreation through saved instance state.
These changes correct the probe integration; they do not change shop appearance.

The public ActivityScenario/Compose test first failed because Apply carried
`EditableText`. The native controls 0.3.3 source confirms that Button deliberately
adds this property alongside `Role.Button`; Android consequently reported an
EditText. The supported modifier now replaces that accessibility contract with
label, Button role and click action, retaining native rendering and a minimum
48×48dp target. The test asserts the public role, absence of editable-text
semantics, target size, input entry and visible applied result. It passed on
API 33, API 37 and the physical API 35 V2250 before the additional stroke override.
The physical hierarchy reports `android.widget.Button` at `[48,2536][228,2680]`
(60×48dp at 480dpi). TalkBack was not enabled.

Actual physical debug screenshots were captured and visually inspected in
[light 100%](evidence/corrected-trial/fluent-fixed-light100.png),
[light 200%](evidence/corrected-trial/fluent-fixed-light200.png),
[dark 100%](evidence/corrected-trial/fluent-fixed-dark100.png),
[dark 200%](evidence/corrected-trial/fluent-fixed-dark200.png), and
[dark 200% with IME](evidence/corrected-trial/fluent-fixed-dark200-keyboard.png).
Labels are readable, the heading clears the status bar and Apply remains above
the keyboard. The images precede the accessible-divider override described below.

The corrected debug/release builds and instrumentation compilation passed on the
existing JDK 25/Kotlin 2.2.10/Compose BOM 2026.02.01/API37 SDK stack. Both release
builds ran R8. The isolated physical release (signed with the local debug key)
accepted `ReleaseProbe` and exposed `Applied: ReleaseProbe`. This proves a trial
interaction, not a production-signing or app-upgrade gate. The minimum-API release
launched and accepted text; its first shell click raced IME relayout, so that
initial capture did not prove the applied result.

### Required scoped accessibility adaptations

Published native Compose APIs were inspected in the core 0.3.11 and controls
0.3.3 source jars. Controls include `Button`, `Label`, `TextField`, `CheckBox`,
`RadioButton`, `ToggleSwitch`, `BasicChip`, `BasicCard`, `FloatingActionButton`,
`AnnouncementCard`, `FileCard`, and `Citation`. This list does not demonstrate
that each is suitable for every app state.

| Family | Required adaptation / fallback and token mapping | Verification status |
| --- | --- | --- |
| Text | Select an explicit Fluent typography role rather than Label's Display default; retain wrapping and system font scale. | Probe Title2/Body1 inspected at 100%/200%. Long product and price text remains downstream. |
| Buttons | Keep native visual tokens; replace editable-text semantics with labelled Button role/click, and enforce minimum 48dp bounds. | Regression red then green; Android hierarchy and physical target measured. Focus and disabled/busy/long-label variants remain to verify in shared pilot. |
| Inputs | Use native TextField, override only the resting divider with neutral `StrokeAccessible`; retain native focused brand stroke and error stroke. | Default divider screenshot measured below 3:1; override verification recorded below. |
| Navigation and app bars | Published tablayout 0.3.5 has TabBar/SideRail; topappbars 0.3.9 has AppBar/SearchBar. Evaluate these against four shop destinations before adopting. | Source API candidates audited; adaptive/back/state behavior requires app-shell verification. |
| Lists, menus, dialogs and sheets | Listitem 0.3.7 has Item/SectionHeader/Divider; menus 0.3.5 has Menu/Dialog; drawer 0.3.10 has BottomSheet/Drawer. Prefer these native Compose APIs when staff semantics fit. | Source candidates verified; selection/errors/long content and controls' accessibility remain downstream. |
| Date selection | Calendar 0.3.3 published source has no Compose declarations; a Fluent-styled Compose date picker is required by the no-legacy-View constraint. Map neutral/brand/status roles, typography and shapes; retain range validation and inclusive dates. | Source gap confirmed; date-picker fallback runtime/state verification remains with sales migration. |
| Progress and feedback | Progress 0.3.7 has CircularProgressIndicator/LinearProgressIndicator/ProgressText; notification 0.3.10 has Badge/Banner/Snackbar/Tooltip candidates. Native transient module 0.3.4 has no Compose declarations. | API candidates audited; announcements, busy actions and contrast require app-state verification. |
| Scanner, photos, QR/printing, storage, sign-in and installer | Keep existing app/platform integrations; theme app-owned surrounding controls with Fluent; platform-owned UI remains platform-owned. | No integration-contract changes in this ticket. |

Rendered screenshot pixels (excluding system chrome) establish light canvas
`#f5f5f5`, foreground `#242424`, field `#ffffff`, placeholder `#616161` and button
`#0f6cbd`/`#ffffff`; dark canvas `#141414`, foreground `#ffffff`, field `#000000`,
placeholder `#d6d6d6`, button `#479ef5`/`#000000`. Native unfocused divider pixels
were `#e0e0e0` on white and `#3d3d3d` on black, below the required 3:1 meaningful
boundary contrast. The source maps those to `Stroke2`. Fluent exposes
`StrokeAccessible` (`Grey38` light / `Grey62` dark), so a supported token override
addresses the identified default deficiency without a fork or toolchain change.

Maven metadata rechecked 2026-11-15 still lists umbrella 0.3.14, core 0.3.11 and
controls 0.3.3 as latest/release; raw metadata is retained under
[evidence/dependencies](evidence/dependencies/). The Compose runtime-livedata
adapter is required by native TextField and is aligned through the app BOM.

At this intermediate checkpoint the runtime gate remained **NO GO** pending final
current/minimum API shrunk runtime. Comprehensive reachable-state/control
verification and actual-app Room workflows remain later-slice requirements. Unperformed TalkBack, assistive
focus/navigation, complete contrast state matrix and platform-layout checks are
not claimed as passes. No binary/runtime incompatibility, fork requirement or
need for a downgrade has been established by the corrected probe.


### Accessible-divider verification

The final native TextField token override was rendered and visually inspected on
API37 in [light](evidence/corrected-trial/fluent-accessible-light100.png) and
[dark](evidence/corrected-trial/fluent-accessible-dark100.png). Exact captured
stroke pixels are `#616161` against white (6.19:1) and `#9e9e9e` against black
(7.84:1), both above 3:1. This resolves the measured resting-boundary deficiency.
The final API37 public input/button accessibility regression passed (1 test).
The physical V2250 disconnected before this last override could be rechecked;
its original dark mode and 1.0 font scale had already been restored.

An [additional published-module API audit](evidence/dependencies/published-compose-api-audit.md)
now identifies native Compose candidates for navigation, bars, sheets, menus,
dialogs, lists, progress and notifications. These are source-level coverage
findings; the trial still pins only core and controls, so other modules' runtime
compatibility and actual staff states must be verified when introduced.
The final phase's API37 emulator showed a **System UI** ANR under host load; after
choosing Wait the trial resumed and rendered. That host/system event is distinct
from the passing Fluent probe and is not classified as a library crash.


Final build checkpoint: the final accessible-divider source passed debug,
release (R8 plus release vital lint) and instrumentation compilation against the
integrated ownership extraction. Normal application build: 7m42s; isolated trial
build: 6m35s. Full logs are in `evidence/corrected-trial/*final-build.txt`.
Integration branch scope/docs through `cc17b78` were merged before this checkpoint;
subsequent merges changed documentation only.

Both shared emulator processes unexpectedly exited gracefully before the final
API37 release install. They were not killed or restarted by this trial. The API37
AVD was relaunched with its existing data preserved. This interrupts the final
shrunk-runtime check; it does not turn a build result into a runtime pass. The
previous physical release interaction and minimum-API release launch/input
predate only the supported resting-stroke token override.


## Final pilot verdict — GO

The final accessible-divider variant was signed with the local debug key and
installed under the isolated `com.nadim.bikepart.compatibilitytrial` package on
both emulators. Each accepted `ReleaseProbe` through the editable field and
exposed **Applied: ReleaseProbe** after the native Apply action. The actual
Android hierarchies are preserved for
[API33](evidence/corrected-trial/fluent-emulator-5554-final-release.xml) and
[API37](evidence/corrected-trial/fluent-emulator-5556-final-release.xml), with
separate command-result logs alongside them. The API33 System UI ANR required
Close app before the probe could run; subsequent app entry/action succeeded.
No shop records or persistence/service contracts were changed.

| Minimal compatibility gate | Result |
| --- | --- |
| Published modular version recheck and static pins | PASS: core 0.3.11 / controls 0.3.3; runtime-livedata aligned to app Compose BOM. |
| Existing supported stack, debug/release resources and resolution | PASS: no downgrade or maintained fork; debug/release graphs captured; resource merging and R8 passed. |
| API33 minimum debug | PASS: ActivityScenario input/Apply/button semantics and 48dp target regression. |
| API37 current debug | PASS: final accessible-divider input/Apply/button regression. |
| API33 minimum shrunk release | PASS: final native input entry and Applied result through actual Android hierarchy. |
| API37 current shrunk release | PASS: final native input entry and Applied result through actual Android hierarchy. |
| Initial light/dark/scaling/IME and essential native-adapter coverage | PASS for probe scope: physical 100%/200% light/dark, pinned Apply above IME; final light/dark divider contrast 6.19:1/7.84:1 measured on API37. |
| Reachable-family mapping and native/fallback candidates | Source inventory and published API audit recorded above; each introduced module and migrated staff state requires its own validation. |

This GO permits the persisted-appearance/shared-button pilot to proceed. Keep the
Button semantics/target and TextField resting-stroke adaptations centralized in
the owning shared components. The pilot must validate Auto/Light/Dark persistence,
all relevant button states, focus, errors, long labels and every unchanged caller.
The final token override was not re-run on the disconnected physical V2250;
that limitation does not erase the min/current emulator checks.

Full-screen migration, complete workflow/Room regression, TalkBack and other
assistive input, all-state contrast, adaptive/system navigation and genuine
production-signed upgrade gates belong to their specified later slices and
release validation. They are unperformed here, not compatibility failures or
production approvals. The parent workflow is separately checking the ownership
extraction's existing workflow failures against baseline. Additional Fluent
modules listed as candidates have not been added or runtime-approved by this
minimal core/controls trial.
