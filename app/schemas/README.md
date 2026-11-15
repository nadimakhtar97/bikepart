# Shop schema history

## Backup retention — operational journal only

Room remains at version 14 and portable exports at format 1. Optional last
acknowledged manifest and reviewed-cleanup fields extend the existing synced
Drive journal. Absent cleanup fields mean no deletion plan. Storage inspection,
permissions, progress and deletion do not advance business revisions or enter
portable backups. See [backup retention](../../docs/backup-retention.md).

## Active-phone replacement — operational journal only

Room and the portable export remain at versions 14 and 1. Optional active-phone,
retirement and pending-takeover metadata lives in the existing non-exported Drive
journal; absent fields mean unknown identity and paused deletion eligibility.
Replacement archives pending jobs and clears coverage before assigning a fresh
writer. No business records, revisions or label identities are changed. Source
provenance remains in the recoverable restore-activation journal. See
[active-phone replacement](../../docs/active-phone-replacement.md).

## Safe activation — no Room migration

Room remains at version 14 and portable export at format 1. Restore reconstructs
and migrates only isolated staging, then atomically selects a complete database,
blob root and fresh operational writer generation through a durable journal.
The existing legacy paths remain the initial root. Saved balances, stock history,
sales/snapshots, carts and dataset/product/SKU identities are imported as records;
activation does not run checkout or advance business revisions. Before-replacement
recovery ZIPs remain accessible after reopening. Operational jobs, coverage and
credentials are excluded. See [safe restore activation](../../docs/safe-restore.md).

## Local export format 1 — no Room migration

Room remains at version 14. The independently versioned local backup codec now
captures all 24 implemented business tables, including label preferences and
the cart sequence, from one committed transaction. ZIP staging, photo pins and
local completion metadata live outside the business database and do not advance
its revision. See [local backup format and interruption contract](../../docs/local-backup.md).
Real-persistence fixtures reconstruct the portable tables/blobs into a fresh
database and reopen it through `Shop`, preserving the complete shop snapshot.
Historical "backup has not landed" notes below describe their original releases.

## Version 14 — durable label preferences

The additive 13 → 14 migration adds `shop_state.labelMedia` and `labelCopies`,
defaulting to Package (50 × 30 mm) and one copy. Existing dataset/product
identities, catalog, stock, histories, carts, aliases, appearance and revisions
remain unchanged. Historical fixtures for versions 1–13 verify preservation,
SQLite integrity, preference changes and reopen.

`Shop.setLabelPreferences` validates positive ASCII whole-number copies within
the signed 64-bit range and commits changed media/copies with one checked
business revision. Unchanged preferences are a no-op. Commit interruption and
revision overflow preserve the previous settings across reopen. Preferences
belong to the durable business state exposed by `ShopSnapshot`, alongside
appearance; a future backup codec must include them. Backup/restore has not
landed yet.

Preview generation is read-only. Both media profiles render the existing
dataset-qualified product QR in black on white with four quiet-zone modules,
an adjacent title of up to three lines and the complete wrapped permanent SKU.
The screen also shows the full title. Price, stock, locations and copy count
never enter the QR or artwork. Changing the physical roll/calibration and
USB printing acceptance remain separate printer work.

## Version 13 — exact external barcode aliases

The additive 12 → 13 migration adds `barcode_aliases` and append-only
`barcode_assignments`. Existing shop identities, catalog, stock, arrivals,
carts, receipts, settings and revisions are preserved. All historical schemas
remain supported; fixtures verify migration, alias linking, reopen, foreign keys
and SQLite integrity. Backup/restore has not landed yet.

An alias is unique by namespace and exact code using SQLite's binary text
comparison. Codes never pass through lookup-name normalization, trimming or
number parsing. The scanner uses the `external` namespace; app QR payloads and
the `bikepart` namespace cannot be assigned as external aliases. Targets carry
an explicit product/template kind and stable identity; `Shop` validates active
ownership inside the write transaction. Historical targets are retained when
products are archived or templates retired, but do not resolve for new work.

Initial links and reassignment each record old/new target, timestamp and durable
revision atomically with ownership. Repeating the current assignment is a no-op.
Reassignment requires the exact assignment revision staff confirmed, preventing
stale and A → B → A confirmations. Conflicts, cancellation, missing/inactive
targets, write interruptions and revision overflow cannot partially mutate the
alias, audit or revision. Stock, prices and cart lines never change on linking.

## Version 11 — safe product archival

The explicit 10 → 11 migration adds `products.archived`, defaulting to false for
every existing product. Identity, catalog values, photos, balances, movements,
unfinished carts, completed receipts, settings and revisions are preserved.
`Shop.archiveProduct` checks zero balance and absence of unfinished-cart lines
inside its write transaction, then advances the durable revision once. Retrying
an archived identity changes nothing. Rejected guards, storage failures and
revision overflow roll back the archive write. Stock, cart, description, price
and threshold writes reject archived products even from stale screens. All ten
historical schemas remain supported by the migration chain and fixtures.

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

## Version 4 — motorcycle fitment and finding locations

The explicit 3 → 4 migration adds motorcycle makes, make-owned models, storage
locations and two product association tables. It preserves every existing row,
identity, stock balance, history, setting and revision. Existing products have
unknown fitment and no finding locations. No motorcycle catalog is seeded.
File-backed version-1, version-2 and version-3 fixtures upgrade to the current
schema, verify durable outcomes through `Shop`, and check SQLite integrity and
foreign keys.

Fitment is represented by model identities: an empty set means unknown, while
assigned fitment requires one or more models. Each model has a make foreign key.
Locations are associations only; there are no location quantities. Product
creation or editing commits all associations and one revision atomically without
changing the combined balance, permanent SKU or opening history during edits.

Makes and locations use shop-scoped normalizer-v1 keys; model keys are scoped
to their make. Independently added lookups survive cancelling a product draft.
Equivalent active names reuse identities without advancing the revision. Retired
names remain reserved. Retiring a make also prevents new selection of its models.
Existing associations may be retained or removed, but retired meanings cannot be
newly selected or re-added after removal. Repeated retirement is a no-op.
Revision-interruption coverage verifies rollback of lookup creation, retirement,
product creation and association edits through snapshots and durable reopen.
Backup/restore has not landed yet.

## Version 5 — immutable product photos

The explicit 4 → 5 migration adds `product_photos`, associating a product with
an immutable SHA-256 content identity and exact byte size. Existing products
retain no photo. File-backed fixtures cover all historical versions 1–4 and
verify identity, typed values, stock, history, settings, revision and integrity.

`Shop` copies selected input into a private temporary file, bounds it to 20 MB,
hashes and syncs it, then publishes it with an atomic rename under a publication
lock. It syncs directory entries before committing the reference and business
revision in one Room transaction. Failed/cancelled input never commits a product
reference; rejected database commits preserve the previous reference. Replacement
uses a new content identity; identical bytes reuse their existing immutable file.
Reads verify size and hash. Image decoding is sampled off the UI thread; missing,
corrupt or undecodable photos use the bundled neutral stock vector.

Files live under `files/product-photos/<SHA-256 of database name>/<content hash>`.
The picker URI is only a form draft: saved products use app-owned bytes. Preview
and file reads do not advance the revision. Original files are never modified.
Completed unreferenced blobs are deliberately retained, including after removal
or a failed database write. No blob garbage collection runs until backup staging
and retention can contribute their pins; future cleanup must use that full union.
Temporary imports are deleted on normal success/failure/cancellation. Process
death can leave an unreferenced temporary file, never a committed partial photo.
Backup/restore has not landed yet.

## Version 6 — local templates and independent catalog copies

The additive 5 → 6 migration adds `product_templates`, with a category foreign
key, retirement state and a version-1 descriptive catalog payload. Templates
have UUID identities but no product row, SKU, balance, price, opening history
or location associations. Historical schemas 1–5 upgrade without changing
existing identities, catalog values, photo references, stock, settings or revision.

The versioned JSON payload retains template-owned custom definitions, complete
active choice domains, exact values, shared category-definition references,
staff-assigned model identities and an optional immutable photo reference.
Payload relationships and types are validated through `Shop` before writes.
This keeps draft/template-owned definitions outside the product-only ownership
foreign key. Shared category fields keep their scope; copied custom fields and
options receive new UUIDs on prefill and again when materialized as a product.
Historical category values and retired meanings are not newly assigned by copies.
Changing an existing template's category retains its previous category values
with history identities in the payload. Later edits retain that history; prefill
omits it. Retired templates display both current values and historical meanings.

Template creation, editing and retirement each commit one revision atomically.
Product materialization encloses product creation, custom definitions/options,
values and photo references in the same transaction and advances exactly once.
Rejected revisions roll back all rows. Opening a draft and cancelling it write
nothing. Product creation still requires separately entered stock and price;
locations are supplied separately. Repeated template retirement is a no-op.

Retired templates remain readable in snapshots and the retired-template browser,
but leave active selection. Existing products and templates never hold mutable
links to source-product custom definitions or to other template payloads. Photo
bytes may be shared by immutable hash; retaining all published blobs keeps old
template references available. Backup/restore has not landed yet.

## Version 7 — reasoned stock corrections

The additive 6 → 7 migration adds `stock_corrections` without changing existing
catalog, opening stock, balances, settings or revisions. File-backed fixtures
upgrade every historical schema (1–6), verify preserved state through `Shop`,
record and reopen a correction, and check SQLite integrity and foreign keys.

Each correction retains its operation identity, product, kind, entered whole
quantity, explanatory reason, transaction-current before/after balances,
timestamp and committed business revision. Its nullable signed movement is
absent for an unchanged physical count. Audit order uses business revision,
so equal timestamps or phone clock changes cannot reorder events. Positive
opening stock plus correction movements reconciles to the cached balance.

`Shop.correctStock` computes count differences within the Room transaction.
Damage and loss remove positive quantities only; counts accept zero. Invalid
input, insufficient stock, integer/revision overflow or interrupted writes leave
no partial audit, balance or revision change. The audit row, optional movement,
balance and business revision commit together. Retrying an operation identity
with the same normalized inputs is a no-op, including after reopen; reusing it
for different inputs is rejected. Compose retains the operation identity and
draft through activity recreation and disables edits while saving.

Product details expose separate count, damage and loss forms and readable audit
history, while showing original opening stock independently of current stock.
These forms reuse the adopted native theme, grouped surfaces and shared fields;
they introduce no new theme tokens or inherited design changes. Backup/restore
has not landed yet.

## Version 8 — optional low-stock thresholds

The additive 7 → 8 migration adds nullable `products.lowStockThreshold`. Existing
products retain no threshold; identities, descriptive values, photos, templates,
balances, correction/opening history, settings and revisions remain unchanged.
File-backed fixtures cover schemas 1–7, including a version-7 correction audit,
and exercise durable threshold settings after upgrade with SQLite integrity and
foreign-key checks.

`Shop.setLowStockThreshold` accepts a blank value to clear or an ASCII nonnegative
64-bit whole-piece count. A changed setting and checked business revision commit
together; an unchanged setting is a no-op. Revision-interruption coverage verifies
rollback and reopen. A positive balance at or below the threshold is Low stock;
zero is always Out of stock, including with no threshold. Status is derived from
the current combined balance, never stored as a second mutable fact. Thresholds
belong to stock management and are not copied into descriptive templates.
Backup/restore has not landed yet.

## Version 9 — persistent quoted carts

The additive 8 → 9 migration adds `carts`, `cart_lines` and `cart_sequence`.
Existing catalog identities, prices, balances, history, settings, thresholds and
revisions are untouched. Cart lines reference products and cascade only when
their owning cart is discarded. The durable sequence prevents reusing an
automatic cart number after discarding a bill. No cart operation writes stock.

`Shop.saveCart` compares the expected edit version inside the transaction before
changing customer labels or lines. A changed cart advances its edit version and
the business revision together. A stale edit or discard is rejected; it never
recreates a discarded cart. An unchanged save is a no-op. Snapshot reads include
all carts and lines in the same transaction as the catalog and balances.

New lines capture the transaction-current selling price. Existing lines retain
their quote through quantity edits and catalog price changes. Removing a line
commits the end of that quote; re-adding captures the then-current price.
Quantities, line multiplication, cart-total addition, cart numbering, edit
versions and business revisions use checked signed 64-bit arithmetic. Invalid
input or overflow rolls the whole transaction back. Informational demand across
other carts uses an arbitrary-precision sum because independent valid carts may
together exceed a single 64-bit quantity; it never becomes a stock reservation.

Current pricing policy: changing a product's selling price reprices matching
lines in every unfinished cart. Affected cart edit versions advance so buffered
drafts based on an older price are rejected. Completed sale snapshots remain
historical and are not rewritten.

The ViewModel debounces cart field edits and reports saved state only after
commit. Returning to the list flushes pending edits through the same write
boundary. Structural actions and navigation are disabled during the commit;
further text input stays buffered and saves serially against the exact committed
cart version before navigation continues. Raw field text and the input session
survive successful autosaves. A failed or
stale draft remains visible for correction, retry or explicit reload. New lines,
removal and discard appear as committed outcomes. Cash checkout remains ticket
11; its entry must use the same pending-edit barrier. Backup/restore has not
landed yet.

## Version 10 — atomic cash sales

The additive 9 → 10 migration creates `sales` and `sale_lines`, preserving open
carts, quotes, numbering, catalog, balances, history, settings and revisions.
All prior schemas remain supported by the explicit migration chain. The unique
`sales.cartId` retains the identity of each completed cart after its open cart
and draft lines are removed in the checkout transaction.

`Shop.checkout` looks up that receipt before validating retry inputs. A new sale
checks the cart edit version, nonempty lines, checked whole-rupee tender and all
current balances. It inserts immutable receipt text (SKU, title, category,
description and fitment), quantity/quote, cash/change, timestamp and revision.
Each sale line also records its signed stock movement and before/after balance.
These inserts, balance deductions, cart completion and revision advancement
commit together. There are no sale update/delete operations and no network or
printing work inside checkout. `Shop.receipt` and snapshot sales expose historical
receipts without resolving their text from the live catalog.

Fixtures cover upgrades from versions 1–9 and SQLite integrity/foreign-key checks.
The version 9 fixture retains an existing customer cart, quote, edit version and
sequence, then completes and reopens its receipt after migration. Backup/restore
has not landed; there is no snapshot codec to extend yet.

## Version 12 — stock arrivals for contextual receiving

The additive 11 → 12 migration creates `stock_arrivals` with a product foreign
key and immutable operation identity, quantity, purchase-unit cost, before/after
balance, timestamp and committed revision. Existing identities, archived flags,
catalog, photos, cart quotes, receipts, balances and revisions are preserved.
Historical schema fixtures now exercise receiving and reopen after migration.

`Shop.receiveStock` commits history, combined balance and one business revision
in the same transaction. Quantities must be positive whole pieces and costs
nonnegative whole rupees; checked balance, total-cost and revision arithmetic
reject overflow. Same-operation retries with identical normalized inputs do
nothing, including after reopening. Conflicting reuse and archived targets are
rejected. Interruption checks cover audit, balance and revision writes. Arrival
history stays readable after archival. No backup/restore codec has landed yet.

Scanning requires no stored scan session or identity migration. Product labels
continue to use the existing dataset/product UUIDs; resolving a scan is read-only.
