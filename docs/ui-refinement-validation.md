# Screenshot-inspired UI refinement validation

Implemented 2026-11-14 against the user-confirmed
[brief](ui-refinement-brief.md) and [UI guidelines](ui-guidelines.md).

## Result

Stock and Carts use Scaffold creation FABs with scroll clearance. Stock rows
retain their detail action and expose labelled menus for editing, receiving,
copying and labels. Shared search fields use quieter rounded surfaces, with
trailing contextual scan actions on stock, cart selection and receiving/catalog
lookup. Stock-health chips scroll horizontally; advanced filters use a scrollable
sheet with explicit clearing. Invalid decimal-filter errors remain visible even
after sheet dismissal.

Sales history has receipt/customer/cart search, date-period chips, a native
custom date-range picker and newest-first daily groups. Device-local day
boundaries and inclusive ranges are explicit. Daily totals use arbitrary-precision
integers so multiple valid receipts cannot overflow their summary. New sales-log
copy uses string/plural resources. Official Material Symbols assets are bundled
locally with source and license records under `third_party`.

Existing Now in Android palette, typography, appearance persistence and primary
navigation remain in use. This pass adapts the attached screenshots' layout
patterns; it does not claim a fresh Figma token inspection or a pixel copy.
Business logic, Room schema, cart quotes, stock reconciliation and immutable
cash receipts retain their existing behavior.

## Automated evidence

- Debug application and instrumentation APK assembly passed.
- Nine JVM tests passed, including local-midnight boundaries, inclusive date
  filtering, search, newest-first ordering and daily totals exceeding Long range.
- All 29 affected instrumentation workflows passed across final runs:
  `ShopWorkflowTest` (10), `SearchWorkflowTest` (1), `CartWorkflowTest` (2),
  `CheckoutWorkflowTest` (3), `ScanWorkflowTest` (11), `LayoutWorkflowTest` (1),
  and `UiRefinementWorkflowTest` (1).
- Android lint passed with zero errors and 30 warnings.
- All 94 generated theme contrast checks passed; minimum normal-text contrast
  remained 4.55:1. `git diff --check` passed.

The new UI workflow uses the actual app and Room storage in light/dark modes at
normal/200% text size. It checks FAB targets, product-menu editing, sheet
dismissal, scanner return with retained search, date-selector entry, recreation
and immutable receipt reopening. Existing workflows cover create/edit/reopen,
quoted carts and stale edits, exact cash/change, checkout recovery, scan routing,
stock thresholds and adaptive compact/rail navigation.

Search coverage now also checks invalid exact-decimal input after dismissing the
sheet and correction after reopening it. Interaction updates tap the visible
title of tall product rows instead of their merged centre beneath a FAB, wait
for sheet dismissal/save completion, and click the New sale FAB without trying
to scroll its fixed Scaffold slot.

Earlier runs interrupted by a frozen emulator/automation disconnect, or using
the old button/inline-filter assumptions, are not acceptance evidence. The AVD
was relaunched without wiping its data. UI inspection helpers remained idle
during the final instrumentation runs. The full unrelated instrumentation suite
was not rerun.

## Rendered and manual checks

Inspected actual captures of Stock, menus, filter sheets, sales logs and the date
selector in light/dark appearances at normal/200% text. Checked wrapping, stacked
prices, menu readability, targets and scrolling. Existing search and checkout
workflows additionally exercise long names, maximum whole-rupee prices, empty
results, validation and keyboard visibility. The layout workflow checks 360dp
compact and 976dp landscape/rail windows with retained search.

Manual custom-range entry with the keyboard open successfully applied
1 January 2000 through 1 January 2000, excluded current receipts, displayed the
empty result and restored the full log through Clear sales filters. Three-button
system navigation was enabled temporarily: the Stock FAB and destination bar
remained above it. Gesture navigation was restored afterward.

Captures and local test logs are review artifacts in `.scratch/refinement-visual/`
and `.scratch/refinement-*.log`. They are not runtime assets.

Physical-phone testing, manual TalkBack/assistive-input checks, fold hinges,
desktop caption bars and predictive-back gestures were not performed. Automated
semantics and touch-target checks do not substitute for those checks.
