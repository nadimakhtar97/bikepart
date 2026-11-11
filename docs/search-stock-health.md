# Search and stock health

Ticket 09 adds manual descriptive product search, combined catalog filters,
optional low-stock thresholds and replenishment alerts. All currently stored
products are active; product archival and backup/restore are separate future
slices.

## Search decision

`CatalogSearch` prepares normalized documents from a consistent `ShopSnapshot`.
It searches title, permanent SKU, description, category, current category/custom
field names, units, text and selected option labels, make/model and finding
locations. Terms combine with AND across these descriptions. Text uses the
existing pinned Unicode normalizer and literal substring matching; regex syntax
in a staff query has no special meaning. Historical category values are not
current product descriptions and are not searched.

Decimal descriptive values remain exact strings in persistence and compare with
`BigDecimal`, independently of scale: `008.00` equals `8.000`, while
`9007199254740993.000000000001` differs from the adjacent final decimal digit.
Numeric query terms also require boundaries when matching text, preventing `8`
from matching `18` or an arbitrary digit inside a generated SKU. An optional
field-specific exact decimal filter handles targeted measurement selection.

Category, make, model, finding location and mutually exclusive stock-health
filters combine with the query. Unknown fitment and unassigned location are
explicit choices. Filtering never changes the product's combined balance.

This is a prepared linear scan, not a database title index. A plain B-tree title
index would not establish efficient arbitrary-substring search across the related
catalog descriptions. Preparation and matching run on `Dispatchers.Default`;
only results belonging to the current snapshot/filter are displayed. Results
render in groups of 40 with a manual Show more action. Opening details and
returning retains filters. An explicit Home alert or Stock navigation starts a
fresh search with the requested health state.

### Reproducible measurement

Run `SearchMeasurementTest` through `connectedDebugAndroidTest`. It creates and
reopens a real Room catalog of 5,000 products in four categories, with exact
decimal values, choice labels, descriptions, 20 models, four makes, ten locations,
varied balances and permanent SKUs. Seven query shapes cover broad/selective
substrings, multiple terms, decimals, absent results, combined filters and exact
SKU lookup. Three warmup rounds precede 70 measured prepared queries. Seven
rebuild-plus-query samples provide a comparison. Timings are diagnostic, without
flaky device-speed assertions; the test asserts known counts independently.

The first debug run on Medium Phone API 37 measured snapshot 526 ms, cold
preparation 3,213 ms, prepared query p50 11.2 ms / p95 40.4 ms / maximum 56.3 ms,
and rebuilding per query median 1,796 ms. This exposed normalization repeated per
field; preparation now normalizes once per complete product document. The final
full-suite debug run measured:

| Operation | Time |
| --- | ---: |
| Consistent snapshot | 167 ms |
| Preparation | 495 ms |
| Prepared query p50 | 5.4 ms |
| Prepared query p95 | 15.6 ms |
| Prepared query maximum | 18.3 ms |
| Rebuild plus query median | 378 ms |

These are observed emulator samples, not timing guarantees.

Prepared scanning fits the measured local catalog and preserves arbitrary text
substring behavior without maintaining another durable index. It remains O(n)
in catalog size, with O(n) prepared memory. Revisit a candidate index such as
trigrams with measured write/rebuild costs if representative larger catalogs or
physical-device timings exceed an acceptable search response. Do not extrapolate
emulator timings to shop hardware.

## Staff behavior and persistence

Home shows labelled low/out-of-stock alert counts linking to Stock. Stock has
health chips and searchable manual catalog-filter dialogs, result counts, clear
actions and truthful empty states. Product rows retain category, current combined
quantity/price, text/icon status, catalog completeness and permanent SKU.

Product details offer a labelled whole-piece threshold editor. Blank clears the
setting, zero is valid, and invalid fractional, negative, grouped or overflowing
input is rejected with announced errors and scrolling to the field. A saved
threshold is announced inline; edits disable during save. A positive balance at
or below the setting is Low stock. Zero is always Out of stock. Alerts do not
open blocking dialogs. Schema 8, its preserving migration, and revision behavior
are documented in [schema history](../app/schemas/README.md).

These additions reuse existing native Material components, shared spacing,
shapes, typography, semantic colors and bundled stock icons. They introduce no
new theme tokens or changes to inherited Figma design decisions. Searchable
choice dialogs and grouped threshold settings are deliberate shop adaptations.

## Review

### Standards

The independent review found missing selected semantics on the default Any
filter choice. Fixed to expose selection consistently. A possible future
maintainability improvement is typed make/location choices instead of optional
IDs plus unassigned flags; the current interface treats their combination as
intersecting filters and has no demonstrated correctness defect.

### Spec

The independent review found stale filters restored when reopening a Home
health alert. A regression workflow reproduced the wrong selected chip. Fresh
alert navigation now clears saved Stock state and applies the requested health;
detail-and-back navigation retains it.

## Validation

Focused real-persistence coverage exercises descriptive/Unicode search, combined
filters, exact decimals beyond floating-point precision, current balances after
corrections, threshold equality, zero without a threshold, set/clear/reopen,
invalid input, unchanged-write no-ops and revision-interruption rollback.
Historical file-backed fixtures exercise every schema 1–7 and SQLite integrity.

The actual app workflow exercises manual selection, combined category/fitment/
location filters, invalid/set/clear thresholds, durable relaunch, repeated Home
alerts and empty results in Light/Dark at 100%/200% system font scale. Captured
screens include long titles, maximum supported prices, keyboard insets and empty
states. Screenshot inspection confirms wrapping and scroll access; very long
amounts/SKUs wrap onto multiple lines at 200%. No fixed row heights truncate them.

Physical-device and screen-reader checks have not been performed.

Final validation on 2026-11-11:

- `gradlew.bat testDebugUnitTest connectedDebugAndroidTest lintDebug`: passed,
  56 instrumentation tests and 2 unit tests, zero failures/errors/skips.
- `node tools/theme/validate.mjs`: all 94 contrast checks passed; minimum normal
  text contrast 4.55:1.
- Kotlin main/test compilation and focused red/green workflows passed; the final
  complete Gradle run finished in 8 minutes 1 second.
- Standards review: one documented finding fixed; no unresolved findings.
- Spec review: one finding fixed; no unresolved findings.
