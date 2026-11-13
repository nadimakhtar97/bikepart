# Safe restore activation

After a successful **More → Restore preview** validation, **Replace shop with
validated restore** opens an explicit replacement confirmation. It warns that
local changes absent from the capture will be replaced and that the previous
shop phone must be retired before replacement-phone backup activation.
Cancelling preserves the current shop. Navigation and writes are disabled while
replacement runs. Save or reload a buffered cart before replacing the shop.

Before changing active state, the app captures a complete local recovery ZIP and
reopens it through the same trusted importer to verify it against the current
snapshot. A safety-copy failure leaves the current shop writable. The successful
copy remains under **Before-restore recovery copy → Save recovery copy…**, including
after reopening. It contains the shop immediately before replacement, including
changes made since the selected restore point. It is separate from coverage and
captures of the restored shop.

## Activation and interruption

`Shop.activateRestore` requires explicit confirmation. It serializes activation
with Drive publication and shop transactions, then reimports the retained input
archive into new staging. This repeats hash, type, relationship, stock, receipt,
photo and trusted-schema validation and any application migrations. Only export
format 1 and the existing trusted Room 1–14 migration chain are supported;
unknown export/schema versions fail closed. No new business-schema migration or
business revision is required for activation.

Each logical database keeps its existing legacy database and photo paths until
the first restore. New roots live under
`files/shop-activations/<database-name-sha256>/<generation>/`, with `shop.db`,
`blobs/` and `provenance.json`. Room checkpoints and closes the migrated staging
database before copying it. Every new file is synced; files and directory
ancestors are durable before activation. New operational metadata lives in the
generation's `no_backup/drive/` directory. No backup credential, old job, coverage
acknowledgement, resumable session or phone-authority marker is imported.

`active.json` is the recoverable activation journal. One AtomicFile replacement
selects the database, blob root and operational generation together; there are
no independent database/photo renames. The entry also retains the recovery-copy
path and source dataset, capture, revision and timestamp. Drive inputs retain
source writer, manifest identity/hash, account and folder provenance. The journal
stream and containing directory are synced. Before this commit, reopen selects
the complete old state; after it, reopen selects the complete validated new
state. Unselected staging/prepared roots are never guessed to be active.

Old roots and safety copies remain retained. Cleanup/retention is deferred and
may consume storage. Activation does not replay checkout, stock movements or
cart operations: saved balances, history, receipt snapshots and unfinished carts
remain records. Dataset/product IDs and permanent SKUs do not change, preserving
existing shared-item QR labels.

All `Shop` business transactions share a per-database gate and check the active
generation, including nested transactions. A pre-restore handle cannot write or
publish after activation. The UI and application observer reopen when the root
changes. Process startup reads the same journal before opening Room or photos.
The app uses one Android process; the gate is shared by its UI, application
observer and WorkManager worker.

## Backup after replacement

The active root receives a new writer UUID and connection identity. The local
target and network preference may be retained, but coverage is empty and jobs
start fresh. The normal startup/periodic reconciliation and immediate UI hint
schedule backup for the uncovered generation. Losing the enqueue hint does not
mark it covered. Scheduling failure does not undo a completed restore.

Publication still checks the existing active-phone marker before uploading any
capture and again before acknowledging completion. Restoring does not seize that
marker or grant writer authority. An existing phone/generation blocks publication
until the separate replacement-phone activation policy is satisfied. No cloud
completion is claimed merely because the source capture came from that target.

## Verification

`RestoreActivationTest` uses real Room/filesystem storage for confirmation,
verified safety copies, actual safety-storage and staged-migration I/O failures,
unsupported input, historical schema activation, stale writer/publication fencing,
fresh coverage/jobs and provenance. Deterministic interruption callbacks cover
safety completion, migration start, copied database, copied attachments, prepared
operational generation, fully durable root, synced journal contents, journal
rename and synced journal activation. Each reopen
checks whole snapshots; stock, receipts, carts, photos and QR identity are checked
through public shop operations. These callbacks simulate interruption; they do
not establish physical power-loss behavior on a particular device.

`RestoreWorkflowTest` exercises cancel/confirm/reopen through the actual app,
substituting only the Google transport after authorization. Confirmation,
completion and recovery-copy screens are captured in light/dark at normal and
200% system text size. Existing preview, validation, backup and shop workflows
remain regression coverage. Physical-phone power loss, real Google authorization
and TalkBack acceptance require device/account access.

## Implementation review

Independent Standards and Spec reviews compared this work with `d2f4d56`.

### Standards

Two lifecycle findings were fixed: post-commit durability/response failures now
wake observers in `finally`, and completion waits for the observer's authoritative
reopened Shop handle by identity. The reviewer rechecked both fixes and reported
no remaining documented-standard or substantive code-smell findings.

### Spec

One shared finding concerned the post-commit root notification. It was fixed and
covered by app and persistence interruption regressions. The reviewer rechecked
the final changes and reported no remaining missing/incorrect requirements or
scope creep.

Review accounting: Standards 2 addressed, 0 remaining; Spec 1 addressed, 0 remaining.

## Execution results

- Kotlin compilation, both APK builds and all **7 unit tests** passed. Final
  Android lint completed with **0 errors, 18 warnings** (manifest, existing
  dependencies/resources and version-catalog guidance). Theme validation passed
  all **94** checks, with minimum normal-text contrast **4.55:1**.
- The isolated focused restore run passed **14/14** persistence and app cases,
  including all nine interruption boundaries, a historical-schema migration,
  real staging I/O failure, unfinished AtomicFile recovery on a previously
  restored root, lost commit responses and the next durable app write.
- The complete instrumentation run finished **137/139**. Two existing fixtures
  needed correction: the cart autosave interruption still locked the legacy
  database rather than the selected generation, and the 200% appearance test
  tapped controls above the restored scroll viewport. The corrected cases then
  passed **2/2** in a focused follow-up. All 139 cases have therefore passed
  across the full run and that follow-up; there was no second all-green full run.
  The backup-photo failure fixture also resolves the active generation and
  passed within the full run. No production cart or appearance behavior changed.
- Twelve actual app screenshots were inspected: confirmation, completion and
  recovery-copy access in light/dark at normal and 200% system text. Controls and
  warnings fit without clipping. A task-owned API 37 emulator used reduced pixel
  resolution with the same logical viewport, independently of other emulator
  work. Final recovery captions distinguish the before-replacement artifact from
  captures of the restored shop, with export feedback routed to its own section.
- Physical-device power-loss, real Google authorization/folder access and
  TalkBack checks remain unperformed. Deterministic transport/interruption tests
  do not establish those acceptance results.
