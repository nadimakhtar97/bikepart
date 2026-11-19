# Typed category fields

Ticket 02 adds category-owned text, exact decimal, single-choice and multiple-choice
fields to stocked products. At initial rollout, staff created categories, field
definitions and choice options inside the product form. Those lookup writes
committed independently, so cancelling the product draft preserved the setup.
Required settings and unit labels belong to definitions; edits validate the
selected category's fields.

## Current workflow

Shared category fields and their choice options are created and managed under
More → Catalog → Categories → the relevant category. Product forms select
existing shared fields and options, with a shortcut to that category's settings.
Product-only custom fields remain with the product, and unsaved draft custom
fields remain in create and template forms.

New shops receive eight optional fields: brake-pad Position (Front/Rear), nut
Thread diameter and Thread pitch (mm), bolt Thread diameter, Thread pitch and
Length (mm), and oil Bottle volume (ml) and Viscosity grade. Existing shops gain
the capability through an additive schema migration without guessing the meaning
of categories that staff may have renamed.

The [schema history](../app/schemas/README.md) documents input limits, exact-value
storage, missing/empty distinctions, decimal-aware ordering, lookup normalization
version 1, owner scopes, migration behavior and revision transactions. Definition
evolution and product-owned custom fields belong to ticket 03; backup/restore is
a later slice and has not landed.

Validation uses the actual Compose app with file-backed Room storage, plus public
`Shop` workflows and a file-backed version-1 fixture generated from the committed
schema. The checks cover all four types, exact close/reopen values, numeric rather
than lexical ordering, missing measurements, required input, cross-owner option
rejection, normalized scoped reuse, inline lookup survival after cancellation,
activity recreation of an unsaved form, integer stock/money constraints, storage
interruption and revision-overflow rollback. The inline lookup checks describe
the initial rollout; the current More-based workflow is described above.
Migration checks retain UUID/SKU,
dataset, price, balance, opening history, appearance and revision and run SQLite
foreign-key and integrity checks.

## Review

Reviewed the task changes from starting commit `8d88c8a` through `a8db1c8`,
then reviewed fixes in `5a0eaa4` with independent Standards and Spec reviewers.

### Standards

No documented-standard breaches. One optional data-clump concern was resolved
by passing a named `AttributeSetup` through the UI callback and forwarding its
settings to `Shop` using named arguments. No remaining actionable findings.

### Spec

One Unicode determinism defect was resolved: platform-dependent normalization
and folding were replaced with bundled ICU4J 76.1 / Unicode 16 data, an explicit
whitespace character set and ASCII-space trimming. JVM and persistence regression
checks cover U+1E030 (which older Android Unicode tables did not recognize), as
well as full case folding and normalized lookup reuse. Future dependency changes
must preserve the tables or introduce a normalizer migration. No remaining findings.

Review totals after fixes: Standards 0 actionable findings; Spec 0 findings.

## Final validation

Inspected actual API 37 emulator captures in light/dark at normal and 200% font
scale, with the decimal keyboard open and a long choice label. Fields remain
reachable above the keyboard, labels and choice chips wrap, and the chip and save
actions satisfy the 48dp checks. Captures are local validation artifacts under
`.scratch/typed-fields-{light,dark}-{1.0,2.0}.png`; they are not runtime assets.
No source theme, typography or component-design decision changed.

The full-suite run found a timing issue in the inline-definition test: it clicked
while an independent lookup commit still disabled the creator. The test now waits
for the action to be enabled. The corrected focused workflow and final complete-suite rerun pass.

Validated 2026-11-11 on the API 37 portrait-phone emulator:

```
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --console=plain --no-configuration-cache
node tools/theme/validate.mjs
```

The suite passes 2 JVM tests and 17 instrumentation tests. Lint reports 0 errors
and 12 warnings: 11 existing starter/resource/version warnings plus a newer-version
notice for the deliberately pinned ICU4J dependency. All 94 generated theme
contrast checks pass, with minimum normal-text contrast 4.55:1. The final test
readiness change was also reviewed with no findings. UI inspection helpers were
stopped during instrumentation because Android shares its automation connection.

Manual TalkBack traversal and physical-phone validation have not been performed.
