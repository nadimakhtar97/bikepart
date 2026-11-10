# First stocked product

Implemented ticket 01, “Create and reopen the first stocked product.” Scope is
offline shop initialization, stocked-product creation and description editing,
editable starter categories, navigation, and persisted appearance settings.

## Persistence and numeric behavior

The file-backed Room tests cover the four seed categories and empty inventory;
create/edit/close/reopen with stable UUID and SKU; opening-history reconciliation;
zero-stock balances without movements; deliberate duplicate products; category and
appearance persistence; invalid/fractional/negative/overflowing inputs; and the
signed 64-bit upper bound. A SQLite trigger injects failure after product insertion.
A separate revision-overflow fixture fails at the end of the transaction. Both
cases retain the preceding complete shop snapshot after rollback/reopen.

Schema version 1 is exported under `app/schemas`. The starter had no database to
migrate. The schema README records the required future data-preserving migration
workflow. No destructive fallback is configured. Backup/restore is a later slice.

## Verification

On 2026-11-11, the API 37 portrait-phone emulator ran with airplane mode enabled.
The app requests no network permission.

```
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain
node tools/theme/validate.mjs
```

The full suite passed: one starter JVM test and nine instrumentation tests (six
shop persistence tests, two app UI workflows, one starter instrumentation test).
Lint completed with no errors and 11 starter resource/version warnings. New
dependencies use the existing version catalog.
The final focused UI rerun includes an explicit wait for the product after fresh
activity launch, avoiding a race with initial Room loading.

The app UI workflows use real Room storage and launch a new activity/ViewModel
after closing the previous activity. They verify retained SKU, description,
quantity and Indian-grouped price. Both brightness modes are exercised at 200%
system font scale, including labelled inputs, announced validation, selected
appearance controls, reachable navigation and 48dp primary targets. The generated
teal roles pass 86 contrast checks; the lowest checked normal-text ratio is 4.58:1.

Home, forms and appearance settings were also inspected in actual emulator
screenshots at 200% text. This found and corrected system-bar icon brightness
when persisted appearance differs from system brightness, and radio-label
alignment at enlarged text size. Focused UI tests and lint passed after the fixes.

Manual TalkBack traversal and physical-phone validation have not been performed.

## Standards

Review compared `90ed032...879b3b9` and then inspected the review fixes.

- Resolved: navigation now uses official filled/outlined selection variants.
- Resolved: typed field errors replace English-message matching.
- Resolved: a named form input replaces six positional callback arguments.

The reviewers found no remaining substantive standards issue. The official filled
and outlined menu symbols have identical geometry; More remains selected through
the Material selection indicator and accessibility selection state.

## Spec

- Resolved: the reopen UI test now closes and launches a fresh activity, clearing
  the previous ViewModel and database connection before reading saved stock.
- Resolved: the test waits for the unique saved product after selecting Stock.

No scope creep or concrete runtime specification defect was reported.

Review totals: Standards 0 substantive findings remaining; Spec 0 findings remaining.
