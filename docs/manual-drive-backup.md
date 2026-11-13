# Manual Google Drive backup

Ticket 18: **Connect Drive and publish a manual restore point**.

Under More, **Connect or repair Drive** opens Android's Google account chooser,
then Google Play services AuthorizationClient requests only `drive.file` for that
explicit account. Choose an existing app-accessible folder or create a named,
visible dedicated folder. The screen displays the saved account, folder name and
ID. **Back up now** requests authorization for that exact saved account; it does
not silently fall back to another account. Consent cancellation and revoked or
expired access leave the saved target and preceding successful coverage intact.
Tokens exist only in memory. The app does not import manual Drive-file edits.

## Publication and interruption

`Shop.driveBackup` is the public operational boundary. Business writes and local
capture continue to use real Room persistence. No schema changes or business
revision changes are needed for this feature. A versioned local capture supplies
CSV tables, schema metadata and immutable photo bytes. Upload work happens after
the database capture transaction has ended.

`noBackupFilesDir/drive/<database-name-hash>/state.json` is an AtomicFile journal.
It is synced with its directory and ancestors. It stores the selected target,
connection identity, installation writer generation, active-phone marker ID,
captured dataset/revision/time, capture path/identity, generated remote IDs,
expected hashes/sizes and resumable session URLs/offsets. It contains no OAuth
token and is excluded from portable business exports and Android automatic
backup. A single in-process mutex serializes publication, target changes and
coverage reads, including multiple Shop instances. This app uses one Android
process; cross-process publication is unsupported.

A pending job resumes its original captured state, even when newer business
edits have committed. The UI continues to report those newer edits as waiting.
A target switch archives the abandoned journal, clears that connection's
coverage and pending job, and retains local staging and remote files. There is
no deletion or retention in this ticket. A capture that failed before a job was
journalled is rebuilt under a new capture identity. Stable generated IDs are
persisted before any upload. Resumable sessions use 256 KiB chunks and query the
server's acknowledged range on retry; expired sessions restart with the same
file ID. A lost creation response is reconciled by downloading that exact ID.
A conflict is an error until the existing bytes are independently verified.

Every dependency is downloaded and verified against its captured size/SHA-256,
including already present files. All dependencies are checked again immediately
before the final `complete-<capture>.json` is uploaded. This manifest contains
exact Drive IDs, hashes/sizes and the portable manifest with schema/table/count
metadata. The final manifest is fetched and compared with the expected bytes.
Only then, after another phone-identity check, does the journal acknowledge the
captured revision for the same account/folder/connection/dataset/generation.
An interrupted or failed attempt keeps the preceding coverage and success time.

`restorePoints` enumerates completion manifests, downloads every dependency,
reconstructs a temporary portable archive, and verifies the complete set before
listing capture times and content counts. `recoverable` returns the same points'
basic identities. Incomplete, damaged, inaccessible or unsupported points are
omitted. Discovery never applies downloaded business data. Selected-point preview
and isolated business validation are described in [restore-preview.md](restore-preview.md).
Safe activation and replacement-phone activation are described in
[safe-restore.md](safe-restore.md) and [active-phone-replacement.md](active-phone-replacement.md).
[Storage inspection and reviewed cleanup](backup-retention.md) add the retained
India-day history window and dependency-safe deletion. Ordinary publication
continues using `drive.file`; storage inspection requests an additional metadata
grant to avoid treating ungranted files as absent.
[automatic-backup.md](automatic-backup.md) describes background scheduling.
The current transport holds one remote file
in memory while uploading/downloading; large-photo streaming is future work.

## Active phone

A fresh dedicated folder receives `active-phone.json` containing dataset and
writer generation. Its ID is journalled before creation. Existing markers must
match; duplicate or missing known markers, existing backups without a marker,
and another dataset/generation stop publication. The marker is checked before
each upload, immediately before the final manifest and before acknowledgement.
A separate [replacement activation flow](active-phone-replacement.md) requires
owner retirement acknowledgement and journals a fresh writer before updating
the existing marker. This is a
cooperative check, not a Drive-enforced exclusion fence: retire the old phone
manually. Reinstall removes the no-backup installation journal, so an existing
folder with another generation requires the replacement activation workflow.

## Validation

The approved seams are the app UI using real Room storage and the public Shop
backup operations with only the external Drive transport substituted.

- `DriveBackupTest`: reopen after interrupted upload, durable original revision,
  lost final response and verified reconciliation, manifest-last visibility,
  newer edits still pending, corrupt dependencies excluded, prior success time
  preserved on failure, stale generation stopped, account/folder switch and
  fresh connection coverage after reopening.
- `BackupWorkflowTest`: real app connection/status controls survive recreation;
  existing local capture/failure workflows remain usable. Its visual matrix
  includes the new controls in light/dark at normal and 200% font scale.

### Real Google acceptance — pending, not claimed

The original implementation run had no real owner account or configured Google
Cloud OAuth client. A subsequent owner-assisted ticket 22 run exercised real
Google consent, a dedicated app-created folder, descendant-byte validation,
replacement restore, fresh-writer backup and interrupted handover on a physical
S23 and an emulator; see [the recorded results](active-phone-replacement.md#owner-assisted-real-drive-acceptance--2026-11-14).
The broader checks below still require additional account/signing conditions:

1. Enable Drive API; configure Android OAuth clients for
   `com.nadim.bikepart` and each installed debug/release signing certificate.
   No server secret belongs in the app. Confirm consent and cancellation using
   the actual signed APK, including an account without a previous grant.
2. Choose/create the visible folder, back up tables and photos, interrupt a large
   upload, restart the app and verify the final manifest/dependencies in Drive.
3. Revoke permission and repair it. Reinstall with the same signing identity,
   then with the intended release identity; record folder rediscovery and exact
   descendant-file accessibility. The list exposes only folders the app can
   access under `drive.file`; it does not grant arbitrary previously ungranted
   folders. A Google Picker recovery flow is not included. Do not infer access
   to all descendants from visibility of a parent folder.
4. Switch between two real accounts and folders; verify the displayed account,
   scope of grants and that old success cannot cover the new target. Reconnect
   the original target and verify generation detection after reinstall.
5. Alter/delete a dependency and the marker in Drive. Verify damaged points are
   not returned and publication stops on an identity mismatch. Confirm the
   preceding complete point remains intact after network/permission failures.

Release-signing/reinstall continuity, arbitrary previously ungranted folder
access, real account switching and screen-reader checks remain unverified.
The ticket 22 results establish access to the specific app-created test folder
and its required descendants only. Deterministic transport tests do not
establish the remaining real-service results.

Primary implementation references:
[Android authorization](https://developer.android.com/identity/authorization),
[Drive uploads](https://developers.google.com/workspace/drive/api/guides/manage-uploads),
[pre-generated file IDs](https://developers.google.com/workspace/drive/api/guides/create-file).

## Implementation review

Compared against starting commit `808edf6` with independent standards/spec
reviews, as required by the implement skill.

### Standards

No documented-standard violations were identified. Two nonblocking judgments:
mutable JSON job records could become typed records, and folder selection and
creation duplicated connection cleanup. The duplicate connection/reset operation
was extracted. JSON persistence follows the existing portable-backup code style;
a larger journal-model refactor is deferred.

### Spec

The initial review found that completed captures displayed upload completion
time and that resumability/acknowledgement tests were incomplete. A follow-up
also found that current capture metadata was not visible during the upload.
These were addressed: completed/pending capture times are separate from success
time; a capture callback updates the uploading UI after durable staging; tests
resume a partially acknowledged job across reopen and change writer generation
during the final manifest response. HTTP tests additionally exercise accepted
chunk/lost response reconciliation and expired session restart with stable ID.
Target changes are serialized with final acknowledgement and clear the old
coverage after any in-flight operation finishes. Session URLs remain only in the
operational job; immutable portable manifest metadata is copied separately.

Final review accounting: Standards had 0 hard findings and 2 nonblocking smells
(1 removed, 1 deferred); Spec had 2 initial findings plus 1 follow-up, all addressed.
Real Google acceptance remains outstanding as listed above.

## Execution results

- Kotlin compilation and Android lint passed on the final source. Lint reported
  0 errors and 17 warnings (dependency/version and existing project warnings).
- Full unit suite: 7 passed. Full instrumentation suite: 117/119 passed, including
  all 13 local-backup, Drive workflow and resumable-transport cases. The failures
  were the existing search theme/large-text workflow (validation-message wait)
  and stock-correction theme/large-text workflow (button visibility).
- Stock-correction workflow passed its isolated rerun. The identical search
  timeout/stack already appears in the pre-change USB validation report, whose
  later isolated search rerun passed; it is a pre-existing intermittent UI test.
- A resumed shared-emulator attempt also encountered another app's concurrent
  instrumentation. A task-owned emulator was created for independent checks.
  After disabling first-boot Play background work and reducing physical render
  resolution while preserving the logical viewport, all 8 backup-specific tests
  passed again. These deterministic checks do not exercise Google authorization.
- Theme validation passed 94 checks. Light/dark screenshots at normal and 200%
  system text were visually inspected after dismissing an unrelated launcher ANR.
  Real Google, signing/reinstall, arbitrary-folder access and screen-reader
  acceptance remain pending as described above.
- Final dedicated-emulator run: **9/9 passed**, including all 8 backup-specific
  tests and the search theme/large-text workflow. Together with the successful
  stock-correction rerun, both original full-suite failures passed isolation.
  The original full-suite result remains 117/119; it was not silently relabelled.
