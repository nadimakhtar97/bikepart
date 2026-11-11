# External barcode aliases

Ticket: `.scratch/shop-implementation/issues/14-barcode-aliases.md`.

After an external scan pauses, staff can choose **Link barcode**, find an active
stocked product or template, and save the exact scanned value. Product choices
show category, combined stock/price, stock health and permanent SKU. Template
choices show their category. Search results paginate so every target is reachable.
The form reuses existing grouped surfaces, Material roles and shop controls;
the inherited Figma tokens and design decisions are unchanged.

Existing ownership is shown before selection. Choosing another target displays
a conflict with the old/new targets and requires **Confirm reassignment**.
Cancelling, returning through manual search, or simply scanning writes nothing.
The scanner shows assignment history and keeps recognition paused until explicit
resumption. Linking never supplies quantities, stock, prices or sale lines.

Codes retain leading zeroes, case, Unicode and whitespace exactly. They are not
lookup labels or numbers. Ownership is unique within namespace/code; the scanner
uses `external`. `bikepart` is a reserved namespace, and payloads beginning with
`bikepart:` cannot be assigned externally, including malformed/unsupported app
QR payloads. App QR parsing and dataset checks retain their strict behavior.

Reassignment compares the confirmed assignment revision inside the same Room
transaction as the new owner, old/new target audit, timestamp and durable
revision. Concurrent/stale confirmations, including A → B → A, cannot overwrite
a changed owner. Repeating the current assignment changes nothing. Unknown,
archived and retired targets keep manual fallback without mutation. Template
aliases only prefill new-product catalog details; sale and receiving reject them.
Product aliases reuse explicit cart quantity, stock-arrival save and catalog
Open existing/Copy actions. Schema 13 is additive; see
[schema history](../app/schemas/README.md). Backup/restore is not implemented yet.

## Validation

`BarcodeAliasesTest` exercises file-backed Room through `Shop`: exact codes,
scoped uniqueness, conflict cancellation, confirmed reassignment, stale/ABA
confirmation rejection, audit/reopen, inactive targets, no-op retries, and
ownership/audit/revision interruption rollback and overflow. Historical schema
fixtures retain catalog, stock, receipts, carts, arrivals, settings and identities
through migration and reopen, then support alias linking with SQLite integrity
and foreign-key checks.

`ScanWorkflowTest` uses the complete app with real Room and substitutes only the
camera transport. It covers staff linking, cancellation, confirmed reassignment,
audit after fresh launch, inactive fallback and aliases in all scan contexts.
The visual workflow checks labelled fields, selected target semantics and 48dp
actions in Light/Dark at 100%/200% font scale, with long names, maximum prices,
zero stock, empty results, keyboard insets and reassignment warnings.
`OfflineQrRecognitionTest` uses the production bundled scanner to decode both
the reviewed QR literal and a real Code 128 barcode with leading zeroes while
networks are disabled. Theme validation passes all 94 contrast checks.

Kotlin compilation, all three unit tests and Android lint pass (zero lint errors,
16 existing warnings). The full 99-test emulator run had 96 passes and three
visual-test failures: an empty-state assertion with the keyboard open at 200%,
a search filter tap before its dialog opened, and a typed-field visibility
assertion with the keyboard open. Tests now dismiss the keyboard before those
visibility/selection checks, await the search dialog, and capture the scanner's
keyboard state separately. All 11 scanner workflows, the search workflow and the
typed-field workflow pass on their final reruns. All 99 checks have passing
results across the full run and focused reruns; the initial full run is not
claimed as green.

Rendered linking, selected products, reassignment warnings, empty results and
keyboard states were inspected in both themes at normal and 200% text size.
Inspection caught rounded pill corners clipping tall product choices; using the
existing medium shape fixes this, with semantic tonal colors for selection.
Evidence is under `.scratch/barcode-aliases-visual/`, `aliases-full.log`,
`aliases-full-results.xml`, `aliases-final-rerun.log`,
`aliases-final-scanner-results.xml`, `aliases-final-search.log`,
`aliases-final-search-results.xml` and `aliases-final-typed.log`.

Only an emulator is available. Physical-phone optical barcode scanning and
TalkBack traversal have not been performed.

## Standards

The independent standards review requested selected semantics and complete
product identification on target choices. Both were added before final validation.
Follow-up review of the final picker and test changes has no open findings.

## Spec

The independent requirements review found no implementation defects, missing
requirements or scope creep.

Review totals: Standards zero open findings; Spec zero open findings.
