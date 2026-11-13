# UI layout precision integration

Reviewed `adcc0b0...67001e1` and integrated with main `1dbfd6f` on 2026-11-14.
The branch changes shared Compose layouts, screen composition, icons and UI
guidance. Persistence and domain operations remain those of main.

## Standards

Three documented-standard findings were corrected during integration:

- Product correction rows now disable navigation while a save is pending.
- Compact stock rows use the documented 8dp shape.
- Shared row and section supporting copy uses the documented body-medium role.

A low-priority duplication heuristic remains: clickable and nonclickable
`ShopListRow` surfaces repeat the same measurement and content layout. This is
a maintainability suggestion, not a documented-standard violation.

## Spec

Three workflow/readability findings were corrected:

- Archived products retain their existing label-preview action. The archival
  appearance test now opens the preview and returns to the archived details.
- Receipt identifiers, saved titles and descriptions wrap fully, preserving
  access to their immutable historical contents.
- Stacked stock prices and list totals wrap without a two-line ellipsis, so
  large grouped amounts can grow at 200% text size.

The comparison uses the branch commit's stated layout requirements, the UI
guidelines, and the existing checkout/archival workflow contracts.

## Validation

- Final debug app and instrumentation APK assembly passed.
- JVM unit tests passed: 7 tests, no failures.
- Android lint passed: no errors, 33 warnings.
- Theme validation passed: 94 checks, minimum normal-text contrast 4.55:1.
- Exclusive emulator run passed: 22 tests across `ShopWorkflowTest`,
  `CartWorkflowTest`, `CheckoutWorkflowTest`, `SearchWorkflowTest`,
  `LabelWorkflowTest` and `ArchivalWorkflowTest` (334 seconds).
- Inspected saved screenshots covering light/dark modes and normal/200% text,
  long product names, maximum grouped prices, receipt identifiers, empty search
  results and checkout validation with the keyboard visible. Values wrap and
  scrolling keeps the content reachable. Captures are local review artifacts in
  `.scratch/ui-precision-review-visual/`.

An earlier shared-emulator run reported timeouts and is not acceptance evidence.
The owner then authorized exclusive use of `emulator-5570` for the integration
run. Unrelated working-copy edits and connected physical-phone data are excluded
from this merge. Physical-device and manual TalkBack checks were not performed.
The entire instrumentation suite was not rerun; this integration used the six
workflow classes affected by the layout changes.
