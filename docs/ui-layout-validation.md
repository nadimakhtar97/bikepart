# Shop layout refresh

Updated 2026-11-14 against [the Android UI guidelines](ui-guidelines.md).

## Design decisions

The approved Now in Android Default palette, applied Roboto roles, 16dp phone
margins, rounded form surfaces and neutral primary actions remain the source of
the visual system. Figma settings node `654:31767` in file
`MapX1cK2WjBKsPS0ZDLIbw` was inspected through design context and its screenshot.
Shop content uses the existing native Material components and bundled shop
icons. News imagery, account controls and illustrative avatars are not imported.

- Home puts quick actions before summary information. Stock attention appears
  once, with labelled links to the affected products.
- Dashboard tiles use two columns when at least 320dp of content width is
  available and the system font scale is below 150%; otherwise they stack.
  Labels reserve two lines, and tiles grow with text.
- Lists use one neutral rounded surface, with 4dp grouping insets and 12dp row
  insets. Forms and settings retain the approved primary-at-8% tonal surface.
  Repeated disclosure arrows and nested card backgrounds are removed.
- Form content stays bounded to 640dp. Section actions and stock status labels
  wrap; numeric form fields stack at larger text sizes. Existing long-price
  handling and keyboard/error scrolling remain in use.
- Secondary actions share a 48dp minimum height. Template lists use secondary
  actions so repeated cards do not each claim primary emphasis. Empty photo
  fields use a small labelled placeholder instead of a blank image panel.
  Template details omit the photo panel when no photo exists.
- More groups appearance, shop, catalog and recovery links. **Shop backups**
  opens a dedicated screen for Drive, local copies, storage, phone replacement
  and restore controls. Their persistence and authorization behavior is retained.
- Primary destinations use bottom navigation below 600dp window width and a
  navigation rail from 600dp. Subscreens retain the centered contextual app bar
  and Up control. Content remains inset from system chrome and the keyboard.

## Verification

Theme validation: all 94 generated-role checks pass, with minimum normal-text
contrast of 4.55:1. Kotlin compilation, regular-app APK assembly, all 7 unit tests
and all 48 selected instrumentation workflows pass. Android lint passes with
no errors and 32 warnings (dependency, resource and Compose style checks).

Actual app screenshots were reviewed across Light/Dark appearance and
100%/200% text, including long names, large prices, empty states, validation,
keyboard insets, carts, checkout, templates, labels, photos and recovery.
The adaptive check passes at 360dp portrait and 976dp landscape window widths,
retaining stock search across the navigation-bar/rail transition. A separate
spot check on the regular install verified Home and the form app bar with
three-button navigation and a tall display cutout. The original gesture mode,
cutout setting, window dimensions and text scale were restored afterward.

Local screenshots and run summaries are in `.scratch/layout-visual/` and
`.scratch/layout-verification-summary.json`. The regular debug APK is
`app/build/outputs/apk/debug/app-debug.apk` and is installed on the emulator.

`LayoutWorkflowTest` exercises the actual app and Room storage at compact and
wide sizes, in Light/Dark appearance and 100%/200% system text. It checks
navigation placement, stock-search continuity across resizing, access to backup
controls, secondary-action touch targets and returning to More. Existing shop
workflow tests cover create/edit/reopen, typed validation, identity, stock,
carts, checkout, labels, photos and recovery controls.

Instrumentation runs use a separate `com.nadim.bikepart.layoutcheck` install and
one test method per Android automation session, with fresh validation-app data.
The combined run encountered a stale Android UI automation connection; those
results are superseded by independent runs. Checkout and Drive-preference
interaction cases passed on fresh reruns, with their assertions retained. The
resize test clears dismissed-input focus before changing window bounds so
focused-field scrolling does not contend with its scroll assertions. UI
inspection helpers stayed stopped while instrumentation owned the automation
connection. Temporary validation packages were removed after verification.

Physical devices, TalkBack, Accessibility Scanner, Voice Access, Switch Access,
fold hinges, desktop caption bars and predictive-back gestures require separate
manual validation; emulator layout checks do not establish those results.
