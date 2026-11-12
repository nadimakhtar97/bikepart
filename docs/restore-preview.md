# Restore preview and validation

Ticket 20 adds **More → Restore preview**. **Find restore points** authorizes the
saved owner account and enumerates only final completion manifests whose required
files can all be downloaded and verified. The list shows capture date/time and
product, sale, unfinished-cart and photo-file counts. Incomplete, unsupported,
damaged and inaccessible points are excluded. A selected point warns that local
changes absent from that capture will not be restored.
Summary buttons use the shared 16dp corners so multiline content remains readable
at 200% system text size.

**Validate selected restore point** reauthorizes the saved account, redownloads
every dependency and binds the selection to the original manifest SHA-256 and
account/folder. A changed manifest, changed connection, duplicate remote identity,
missing file, size/hash mismatch or conflicting portable metadata fails validation.
The current dataset need not match: a replacement phone must be able to inspect
the owner's existing shop. This does not designate that phone as an active writer.

## Isolated preparation

`Shop.validateLocalRestore` and `Shop.driveBackup.validateRestore` return a
disposable `ValidatedRestore`. Its snapshot and photos represent the prepared
copy. Neither API replaces active state, replays checkout, advances a business
revision, acknowledges backup coverage or uploads to Drive.

Staging lives in a random child of the database's no-backup Drive directory under
`restore/`. It holds an input copy, a separate database and a separate blob root.
Failure or cancellation removes that attempt; closing the result removes it too.
The ViewModel retains successful staging across activity recreation and closes
it on selection/connection changes and ViewModel disposal. Process death can
leave an unused staging directory; it is never automatically opened or activated.

The portable UTF-8 format remains version 1. Room schema versions 1–14 have bundled
trusted definitions. The importer verifies the table/column sets, declared types,
nullability, primary keys, foreign keys and Room identity against the matching
bundled version. It creates tables and unique indexes from **bundled** SQL, never
from backup-provided SQL. Values are inserted with explicit integer parsing and
null checks; SQLite checks keys, uniqueness, relationships and integrity. Existing
application migrations upgrade only the staged database to version 14; Room then
validates the resulting schema. Unsupported versions fail before activation can
be considered. No active schema change is introduced by this ticket.

Business validation checks all implemented tables, including normalized scoped
names, stable IDs, booleans/settings, field ownership and value types, exact
decimals, choice domains, historical fields, template payloads, fitment/locations,
photo dependencies, cart numbering/quotes, archived-product constraints and
barcode assignment chains. Retired meanings and incomplete fields already allowed
by normal shop operations remain readable. Stock validation starts from opening
stock and walks arrivals, corrections and sale deductions in committed revision
order, checking every before/after balance and final saved balance. Receipts retain
their historical text and quotes; checked arithmetic verifies quantities, line
totals, sale totals, cash and change without deducting stock again.

Successful validation reports a prepared copy and explicitly says the current
shop is unchanged. Activation, safety copies and an activation journal belong to
the next ticket; no replacement control is supplied here.

## Verification

The approved seams are real-persistence Shop operations and the actual app UI,
substituting only the external Drive transport after authorization.

- `RestoreValidationTest` covers exact Unicode/decimal/null/photo/receipt round
  trips, malformed quantities/money/types/IDs/relationships, stock/sale mismatches,
  corrupt/missing dependencies and isolated supported-schema migration.
- `RestoreWorkflowTest` covers replacement-dataset discovery, incomplete/corrupt
  point exclusion, changed/missing selected dependencies and app preview/validation
  across light/dark at normal and 200% system font scale, including recreation.

Real Google authorization, previously ungranted folder/descendant access, physical
device and TalkBack acceptance require owner/device access and are not established
by the deterministic transport tests. The existing Google acceptance checklist in
[manual-drive-backup.md](manual-drive-backup.md) still applies.

## Implementation review

Independent standards and specification reviews compared the restore changes
with starting commit `63efc9a`. Concurrent automatic-backup changes were excluded.

### Standards

Two findings were addressed: supported legacy category normalization keys remain
accepted, and ordinary catalog writes and restore share the complete custom
definition/option identity uniqueness invariant. The reviewer rechecked the fixes
and found no remaining actionable issue.

### Spec

Two findings were addressed: historical custom values still require the owning
product, and template option IDs cannot collide with definitions or other fields'
options. Checksummed hostile fixtures cover both cases. The reviewer rechecked
the fixes and found no remaining issue.

Review accounting: Standards 2 addressed, 0 remaining; Spec 2 addressed, 0 remaining.
The template-identity finding appeared on both axes.

## Execution results

- Final Kotlin compilation, app/test APK builds and all 7 unit tests passed.
  Android lint completed with 0 errors and 17 warnings.
- The full instrumentation run completed **130/131**. The barcode workflow
  `staffLinkExactExternalBarcodeThenCancelAndConfirmReassignment` could not find
  its target field during text replacement. That unchanged case passed two
  isolated follow-ups; the full result remains 130/131.
- Final focused run: **7/7**, comprising all 6 restore persistence/UI cases and
  that barcode workflow. Supported staged migration, malformed singleton IDs,
  integer-width overflow, fractional metadata, canonical QR identities and
  interrupted downloads are included. Regression fixtures failed against the
  earlier app before their guards were applied.
- Twelve actual app screenshots were inspected: complete-point summaries,
  validation success and empty states in light/dark at normal and 200% system
  text. Multi-line summaries use shared 16dp corners to avoid clipping. The
  task-owned emulator rendered at reduced physical resolution with its logical
  viewport preserved. Theme validation passed all 94 checks, with minimum
  normal-text contrast 4.55:1.
- The ticket-only source snapshot also compiled independently of concurrent
  automatic-backup work. The final merged checkout compiled and passed its unit,
  lint and focused instrumentation checks after that work committed separately.

Physical-device, real Google authorization/folder access and TalkBack checks
remain pending; no active-state replacement was implemented or exercised.
