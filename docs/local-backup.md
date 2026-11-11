# Consistent local shop captures

Under **More → Local backup**, **Capture local backup** creates a verified ZIP on
this phone. **Save a copy…** copies that exact artifact through Android's document
picker. A successful capture is a local recovery artifact, not confirmation of a
Drive backup. Ordinary shop writes remain available during staging. Only committed
changes are captured; text still buffered in an open editor is not committed state.
The screen shows the captured revision/time and whether newer changes exist.
Failure preserves the previous successful capture, including after reopening.
Reopening selects the highest committed revision, then capture time, so a phone
clock correction cannot make an older revision replace a newer local success.

## Format 1 (independent of Room version 14)

Every archive has a random capture UUID and contains:

- `tables/<name>.csv`: one file for each of the 24 implemented business tables,
  including retired/archived records, lookup normalization keys, settings, durable
  counters, attribute history, stock history/balances, cart quotes, receipt
  snapshots/movements and barcode assignments. Credentials and operational jobs
  are not included. The explicit table allowlist fails closed when schema coverage
  changes; future entities require a codec update/classification.
- `schema.json`: Room version and identity hash, SQLite table/index DDL for
  provenance, ordered columns, declared SQLite types, nullability, defaults,
  primary keys, foreign-key relationships and row counts. DDL is provenance;
  an eventual restore implementation must validate it rather than execute
  untrusted input. Restore/activation is separate work.
- `blobs/<sha256>`: immutable photo bytes, deduplicated across products and
  templates, including retired templates.
- `manifest.json`: written last; export and Room versions, dataset/capture IDs,
  captured business revision/time (epoch milliseconds), exact relative file
  identities, byte sizes and SHA-256 hashes, table metadata/counts and required
  blob identities/sizes. The manifest does not hash itself.

CSV uses UTF-8 without BOM, comma separators, CRLF record terminators and a header.
Every non-null field is double quoted; a quote inside a value is doubled. Embedded
Unicode, CR and LF are preserved. SQL NULL is the **unquoted** token `\N`;
empty text is `""` and literal backslash-N is `"\N"`. Signed integers are base-10
ASCII, never floating point. Decimal attribute values are exported as their exact
stored text, including inside template JSON. Booleans retain SQLite 0/1. JSON
revision/time fields use decimal strings; byte sizes and counts are JSON integers.
Nested template/choice JSON remains quoted CSV text and retains its own identities.

`LocalBackup.read` verifies required file sets, hashes/sizes, supported export
version, table metadata/counts, captured dataset/revision and photo dependencies,
then decodes CSV without losing null/empty values. It is a codec reader, not a
restore operation or comprehensive business-state validator. `inspect` performs
the same verification while streaming blob checks to bound photo memory use.

## Consistency, interruption and pins

One Room transaction captures all table rows, schema provenance, dataset/revision,
capture time and immutable photo references into memory. ZIP encoding and photo
reads occur after releasing that transaction. Photo import publishes immutable,
hash-addressed files before committing a reference; subsequent edits cannot change
the captured bytes.

Files live under `files/local-backups/<database-name-sha256>/`. Before staging,
`<capture-id>.pins.json` durably records the captured revision and required photo
identities. All imported photo blobs are currently retained, including the short
interval between reading references and writing the pin file. **Do not introduce
photo garbage collection without coordinating that interval with capture and
honoring all retained/pending pin files.** No cleanup deletes pinned photos.

Staging writes `<capture-id>.partial`, finishes the ZIP and fsyncs it, rereads and
verifies every required byte, then atomically renames it to `<capture-id>.zip` and
fsyncs the directory. New directory ancestors are synced as well. Only verified
`.zip` files are complete candidates on reopen. Interrupted `.partial` attempts
and pin records remain safely retained and are never resumed or relabelled; retry
captures current committed state under a new UUID. Cleanup/retention is deferred;
incomplete attempts can consume storage. Neither capture nor export changes a
business revision or records cloud coverage.

The filesystem/ZIP APIs run on Dispatchers.IO. Capture currently holds database
rows in memory, suited to the existing local shop scope; it does not hold all
photos in memory while staging or checking the latest artifact. Document-provider
copy failures leave the internal verified artifact available for retry; durability
of an external destination is governed by that provider.
