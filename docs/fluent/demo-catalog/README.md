# Fluent Android demo catalog

Captured 2026-11-16 from the user-supplied `FluentUI_Demo_v0.3.14.apk`, installed with `adb install -r` into the existing `bikepart-api33` emulator (`emulator-5554`). No emulator wipe or upstream rebuild was used. Package: `com.microsoft.fluentuidemo.df`.

APK SHA-256: `8248fcff93e77531e28bd753e7a2c8918ed1fcebb7818fd452a8b429d750ecfc`.

The emulator runs Android API 33, 1080 × 2400 pixels, density 420, system font scale 1.0. Captures use the demo's initial Fluent Brand, Accent enabled and light appearance. The clock/date and runtime sample values are incidental.

- [Component index](INDEX.md): every one of the **37 V2 + 24 V1 = 61** launcher entries, its screenshot, pinned source and proposed shop use.
- [Source map](SOURCE-MAP.md): exact demo paths, Fluent imports and resource references.
- [Machine-readable inventory](inventory.json) and [source metadata](sources.json).
- [Theme menu screenshot](theme-menu.png) and [layout](theme-menu.layout.json): Accent, Appearance and all seven brands together.
- [Theme implementation research](../demo-source.md): exact `AppProperties.kt`, demo alias-token subclasses, state ownership and licensing boundaries.

## Coverage and limitations

**60 entries rendered, one failed on launch.** [V2 People Picker](23-v2-people-picker.png) consistently crashes with `IllegalStateException: Size(-10 x 0) is out of range`, at `AvatarGroup.kt:147`. The [crash log](23-v2-people-picker-crash.txt) is the failure evidence. Its PNG shows the previous compatibility-trial app exposed after the demo process died; it is explicitly a failed-launch screenshot and does not depict People Picker. The separate [legacy PeoplePickerView](50-people-picker-view.png) renders, but does not establish V2 compatibility.

Each final PNG was visually inspected before the next device action. Layout JSON accompanies every screenshot. Layout and PNG are consecutive observations, so animation progress, status time and transient text can differ. The V2 Snackbar PNG was taken immediately via `adb exec-out screencap -p` to preserve the transient message; its layout records the same message from the preceding trigger. Animated legacy Progress required `android layout --no-idle` because its changing progress prevents an idle tree dump. Some long examples extend below or beyond the viewport; the catalog records the visible representative sample, not all variants in each scrollable page.

Configuration-only demonstrations were triggered before recording their rendered sample: ActionBar Start Demo (V2 and V1), Bottom Drawer Open, BottomSheet Expand, Dialog Show, Drawer left slide-over Open, Menu Open, SegmentedControl Pill Button expansion, Snackbar Show, StackableSnackbar Add twice, legacy BottomSheet Show, DateTimePicker Show, Drawer Show Left, PopupMenu Show, indefinite Snackbar, TabLayout two segments and Tooltip Show. Separate `-rendered` images retain configuration-page captures where useful. The Acrylic Pane capture includes its translucent search surface. PersistentBottomSheet is captured in its initial visible collapsed state.

This catalog validates the supplied demo APK on this emulator only. It does not validate BikePart screens, Android API 37 behavior, process-restored settings, dark appearance, 200% fonts, screen readers, physical devices, all component states or the app's required workflow tests. V1 is legacy View comparison evidence; use Compose V2 when implementing the migration, and select fallbacks deliberately for missing or crashing components. Microsoft employee names, photos, Office branding and email content are sample data, not BikePart requirements.

## Reproduction and upstream provenance

The authoritative inventory is [Demos.kt at v0.3.14](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/Demos.kt). `DUO_DEMOS` repeats existing legacy entries and is not nine extra components. Source paths were resolved by class declaration (for example, `V2AcrylicPaneActivity` lives in `V2AcrylicActivity.kt`, and `V2StackableSnackbarActivity.kt` is outside `demos/`).

Launch `com.microsoft.fluentuidemo.V2DemoListActivity`, choose V2 or V1 Components, scroll to the exact indexed label and tap it. Use the noted sample trigger where required. Capture with `android screen capture --device emulator-5554 -o <path>` and visually inspect immediately. Use `android layout --device emulator-5554 --no-idle` for the adjacent tree. Component activities are not exported, and the supplied APK is not debuggable, so navigation used the real launcher UI.

The theme popup is implemented by [AppProperties.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/microsoft/fluentuidemo/AppProperties.kt). Its seven brands are Fluent Brand, OneNote, Word, Excel, PowerPoint, M365 and MS Teams; non-default palettes live in demo source [CustomizedAliasTokens.kt](https://github.com/microsoft/fluentui-android/blob/v0.3.14/FluentUI.Demo/src/main/java/com/example/theme/token/CustomizedAliasTokens.kt). Those demo subclasses must be adapted with MIT attribution if copied; their package is not an SDK-published API. See the linked research before implementing theme selection.

Screenshots reproduce the Microsoft Fluent UI demo for project reference. Upstream source is [MIT licensed](https://github.com/microsoft/fluentui-android/blob/v0.3.14/LICENSE). This catalog copies no upstream implementation code; import lists and source links identify the corresponding first-party examples.
