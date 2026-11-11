# Product label previews

Ticket: `.scratch/shop-implementation/issues/15-label-previews.md`.

Open a product and choose **Preview product labels**. Staff explicitly select
Package (50 × 30 mm) or Shared bin (70 × 40 mm), enter positive whole-number
copies, and choose **Preview label**. Changing the profile shows the required
physical roll and calibration guidance. **Save label preferences** separately
commits the default media/copies; previewing itself writes nothing. Saved
preferences survive app restart and advance the business revision only when
changed. A polite live region announces the committed default.

The black-on-white artwork places the actual QR on the left, with up to three
title lines and the complete wrapped SKU on the right. Long titles are also
shown in full below the artwork, with the complete SKU available as accessible
text. Price, stock and location never enter the artwork. Both sizes encode the
validated `bikepart:1:<datasetUuid>:product:<productUuid>` identity, with a
four-module quiet zone and whole-module raster rendering. Catalog changes,
repreviewing and reprinting retain that identity and never supply a quantity
or change stock. Labels identify products, including shared bins, rather than
individual pieces or locations.

The screen reuses the existing native grouped surfaces, radio rows, fields and
buttons from the approved Now in Android adaptation. Artwork is a fixed-ratio
printer preview; its black/white ink and fixed text sizes intentionally differ
from the surrounding semantic app colors and scalable text. The screen's full
title/SKU and controls follow system text scaling. No inherited theme tokens
or design decisions change. Physical output and the USB print action belong
to the printer ticket.

Schema 14 adds preferences to `shop_state`; see
[schema history](../app/schemas/README.md). Backup/restore has not landed yet.
Preferences are business state in `ShopSnapshot`, ready for inclusion in that
feature's codec; this slice does not claim an implemented backup.

## Validation

`LabelQrTest` independently decodes the final reviewed payload and checks the
quiet zone. `LabelPreviewTest` decodes QR from both final rendered artwork sizes,
uses file-backed Room through `Shop`, checks invalid copies, no-op writes,
revision advancement, catalog/price edits, identity, stock, durable reopen,
commit interruption and revision overflow. Historical schema fixtures for
versions 1–13 preserve existing shop data and exercise label preferences after
upgrade, including foreign-key and integrity checks.

`LabelWorkflowTest` uses the complete app with real persistence. Staff select
both profiles, validate copies, save preferences, recreate/relaunch the app,
edit the product through its form, and preview its stable QR again. Long titles
and full SKUs remain accessible in Light/Dark at 100%/200% system text. Actual
app captures for all eight profile/appearance/text combinations were inspected
under `.scratch/label-previews-visual/`. The theme validator passes all 94
contrast checks (minimum normal-text contrast 4.55:1).

The first catalog-edit UI check timed out because it changed the catalog using
a separate database connection while retaining the active ViewModel snapshot.
The final test uses the staff edit form and passes. No production refresh
workaround was added for that test setup.

Final verification completed on 2026-11-12:

- All four unit tests pass, including final-payload QR encode/decode.
- The full instrumentation suite ran once: 104 tests, 102 passed and two
  existing UI workflows timed out (checkout after quantity editing and search
  threshold validation). Both failed methods pass on isolated reruns without
  production changes. Their timeout cause remains unconfirmed; this is not
  claimed as a green full-suite run.
- All four label instrumentation tests and all 13 historical migration tests
  passed in the full run. All 104 tests have passing results across that run
  and the focused reruns.
- App/test Kotlin compilation and Android lint pass: zero lint errors and 16
  warnings. The theme validator passes all 94 checks.

Local evidence: `.scratch/labels-full.log`, `labels-full-results.xml`,
`labels-checkout-rerun.log`, `labels-search-rerun.log` and
`label-previews-visual/`, all under `.scratch/`.

## Standards

Standards review against starting commit `b440b83` found one announced-feedback
violation: the saved-default status initially lacked live-region semantics.
Polite live-region semantics resolve it; the final Standards review has zero
outstanding findings and no actionable baseline smells.

## Spec

The independent Spec review has zero findings: no missing or partial
requirements, scope creep or incorrect implementation was identified.

Review totals: Standards — one finding fixed, zero outstanding; Spec — zero
findings.

Physical-phone, TalkBack listening, consumables and printed readability checks
have not been performed. Printer calibration, real TE244/USB transport and
physical sample acceptance remain the printer ticket's responsibility.
