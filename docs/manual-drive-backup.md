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

`recoverable` enumerates completion manifests, downloads every dependency,
reconstructs a temporary portable archive, and uses the existing verifier to
check the complete set. Incomplete, damaged, inaccessible or unsupported points
are omitted. Discovery never applies downloaded business data. Actual restore,
preview/activation, retention, background scheduling and replacement-phone
activation remain separate tickets. The current transport holds one remote file
in memory while uploading/downloading; large-photo streaming is future work.

## Active phone

A fresh dedicated folder receives `active-phone.json` containing dataset and
writer generation. Its ID is journalled before creation. Existing markers must
match; duplicate or missing known markers, existing backups without a marker,
and another dataset/generation stop publication. The marker is checked before
each upload, immediately before the final manifest and before acknowledgement.
A replacement activation flow is deliberately not supplied here. This is a
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

No real owner account or configured Google Cloud OAuth client is supplied by this
checkout. These checks require the owner's configured project and device:

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

Physical-device Google authorization, signing/reinstall continuity, folder
rediscovery/descendant access, real account switching and screen-reader checks
remain unverified. Deterministic transport tests do not establish those results.

Primary implementation references:
[Android authorization](https://developer.android.com/identity/authorization),
[Drive uploads](https://developers.google.com/workspace/drive/api/guides/manage-uploads),
[pre-generated file IDs](https://developers.google.com/workspace/drive/api/guides/create-file).
