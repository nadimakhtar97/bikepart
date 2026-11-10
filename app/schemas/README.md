# Shop schema history

`com.nadim.bikepart.shop.ShopDatabase/1.json` is the first persistent shop schema.
The starter application had no database, so there is no earlier shop schema to
migrate. First use seeds four categories in the same initialization transaction
as the dataset identity and revision zero. Reopening never reseeds an existing shop.

Room is accessed through `Shop`. Its database, DAO and entities are internal and
the database instance is private. Product creation saves the generated UUID and
unique permanent SKU, whole-rupee price, balance (including zero), positive opening
history and checked revision advance in one transaction. A zero opening has no
movement. The opening table records the origin and timestamp of positive balances.
Description edits never rewrite identity, price, balance or opening history.

For each future schema change:

1. Increment the Room version and commit its generated JSON alongside all old JSON.
2. Register an explicit data-preserving migration in `Shop.open`; never use
   destructive migration fallback or delete the shop database to fix an upgrade.
3. Test upgrades from every supported historical schema using a file-backed
   fixture, verify identifiers, prices, balances, history, settings and revisions
   through `Shop`, and run SQLite foreign-key and integrity checks.
4. Extend workflow and ledger reconciliation coverage. Extend backup/restore
   coverage once that feature exists; it is not present in this first slice.

Signed 64-bit integer input is parsed without rounding. Only ASCII digits with
optional surrounding whitespace are accepted. Monetary/quantity grouping is a
display concern, never an input conversion. Revision overflow rejects and rolls
back the entire write. The schema deliberately uses integer columns rather than
floating-point stock or money.

## Version 2 — typed category fields

Version 2 adds `category_attributes`, owner-scoped `attribute_options`, and
`attribute_values`, plus `shop_state.normalizerVersion`. The explicit 1 → 2
migration preserves all existing rows, dataset identity and business revision.
It does not infer starter fields from renamed categories. Only a new shop seeds
the eight optional definitions and Front/Rear options, within initialization at
revision zero. The generated version 1 schema remains the migration fixture.

Values distinguish absent rows, empty text, exact decimal text and option-ID
sets (JSON arrays). `Shop` validates type, owner, required values and option
membership before writing. A product write includes values, stock origin and
revision in one transaction. Each independently created category, definition or
option commits its own revision; reuse changes no revision. Cancelling a form
has no lookup deletion side effect. Backup/restore has not landed yet.

Decimal input accepts surrounding whitespace, an optional ASCII sign, 1–18
integral digits and an optional decimal point followed by 1–12 fractional digits.
Leading and trailing zeroes are retained. Exponent notation, nonfinite values,
grouping separators, bare decimal points and precision overflow are rejected;
there is no rounding. Missing measurements have no value row; they are never
converted to empty text or zero. `productsByDecimal` compares `BigDecimal` values,
sorts missing measurements last and uses product identity for deterministic ties.
Quantity and money continue to use signed 64-bit whole-number integer columns.

Lookup normalizer version 1 pins ICU4J 76.1 / Unicode 16 tables for NFKC and
case folding, independent of Android or Java runtime versions. Its explicit
Unicode 16 White_Space character set avoids platform regex Unicode properties.
It applies NFKC, collapses Unicode White_Space to one
ASCII space, trims it, then uses locale-independent full default Unicode case
folding. Readable display labels retain case. Category names are shop-scoped,
definition names category-scoped, and options definition-scoped. A same-name
field with incompatible settings is rejected rather than changing its meaning.
Legacy category keys are preserved on migration: category creation/rename compares
normalized display names inside the transaction, so pre-existing keys cannot
allow new equivalent duplicates. Existing category identities are not merged.
Future changes to these rules require a new normalizer version and migration.

ICU4J is deliberately pinned: [ICU 76 release / Unicode 16](https://unicode-org.github.io/icu/download/76.html).
Dependency updates must preserve these tables or introduce a normalizer migration.

## Version 3 — catalog definition evolution

The explicit 2 → 3 migration adds product ownership and retirement to definitions,
retirement to options, and stable historical-value identities. The value-table
rebuild copies every version-2 value without conversion. Both version-1 and
version-2 file-backed fixtures exercise upgrades to the current schema, retaining
identity, prices, balances, opening history, settings, exact values and revisions.

Definition names are unique by normalized name within `category:<id>` or
`product:<id>` ownership. Retired names remain reserved. Promotion changes scope
only on explicit request and rejects every category-name collision, even when
types happen to match. Staff must choose a distinct name and meaning; no field or
option identities are merged. Custom definitions follow their product across
category changes. Independent lookup creation survives cancelling an edit.

Retired definitions and options stay in snapshots for readable existing values.
An edit may retain a retired field unchanged, or omit it; a retired option may
only remain in a product that already selected it. No newly selected retired
meaning is accepted. Required validation uses applicable active definitions;
`missingRequired` flags older incomplete records without changing their stock,
price or availability. There is no sales implementation yet in this slice.

A type replacement creates a new definition under a distinct name and retires
the old one in one revision. It never converts values or shares choice options.
Category changes retain previous category values with immutable history IDs;
repeated moves preserve each occurrence, including when returning to a category.
Staff may explicitly copy text/decimal values with matching type and unit to
active target definitions. Choices require explicit selection in the new field
because option identities are definition-owned. Both the original historical
value and the copied value survive. Product custom values remain current.

Every business write advances the durable revision in its transaction. Reusing
lookups, repeating retirement and setting an unchanged required flag are no-ops.
Revision-commit interruption tests verify rollback of creation, promotion,
retirement, required changes, replacement and category moves through public
snapshots and close/reopen checks. Backup/restore has not landed yet.
