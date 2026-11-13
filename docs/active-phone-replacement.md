# Active shop phone replacement

Ticket 22 builds on [safe restore activation](safe-restore.md). Restoring shop
records and designating the active backup writer are separate owner actions.
Dataset/product/SKU identities remain unchanged; writer generations never serve
as QR identities.

The owner first restores the intended shop, then reviews the selected account,
folder, dataset, restored capture and current remote writer. Replacement requires
an explicit acknowledgement that the old installation has been retired. Stop
using the old phone, verify the replacement and recovery copies, then uninstall
the old app or clear its app data. An offline old installation can still edit
its local shop until the owner retires it. Drive does not provide this app with
a per-installation revocation fence.

Replacement checks that the marker still matches the reviewed identity and bytes,
then durably journals a fresh writer UUID before updating `active-phone.json`.
Old jobs are archived; current coverage and capture timestamps are cleared. Source
restore provenance remains in the activation journal and is shown independently
of current backup coverage. Only a verified new-generation completion can cover
this writer's shop state.

A lost marker-update response leaves takeover pending. **Check active phone**
reads and reconciles the current marker without repeating the takeover request.
Publication remains blocked until that check confirms this writer. A changed,
missing, duplicated, malformed or inaccessible marker preserves recovery history.
Review a new replacement attempt only after resolving the handover with the owner.

Cooperative publication checks the marker before every content upload, before
the completion manifest and before acknowledgement. A known mismatch stops those
actions and gives the owner corrective next steps. Local retirement blocks cloud
publication but is not a remote lock on offline business edits.

Automatic deletion eligibility starts paused, stays paused during uncertain or
pending takeover and resumes only after a confirmed active state. The status is
the last observed state, not a lease. Cleanup must call `requireActivePhone` again
immediately before each destructive request; it must never use a cached status as
authorization. [Ticket 23's storage workflow](backup-retention.md) applies that
check to every manifest/dependency deletion. Phone replacement itself does not
delete backup history.

The cooperative check cannot make a read followed by a Drive write atomic. A
competing takeover can win after a check or after an update. Final readback detects
observed races, and subsequent clients stop on mismatch; it cannot guarantee that
an in-flight old client is fenced. This is why retirement is an owner procedure
and uncertain handovers preserve recoverable history.

## Verification

Approved boundaries are real Room/filesystem Shop workflows and the actual app
UI, with only external Drive transport substituted for deterministic faults.
`PhoneReplacementTest` covers restored provenance and explicit retirement,
distinct writers and fresh coverage, retired reconnection, stale reviews,
durable pending takeover after a lost response, read-only reconciliation,
content-upload and final-publication races, competing takeovers and uncertain
listings. The original restore point remains recoverable throughout failures.
`DriveUploadTransportTest` checks the selected marker's media update, permission
failure and Android's actual PATCH-method support without making a request.

`PhoneReplacementWorkflowTest` exercises the app's restored identity/source
summary, acknowledgement and activation states, activity recreation, explicit
identity check and retirement cancellation. Google authorization and transport
are substituted at the existing external boundary. All four light/dark ×
normal/200% text combinations passed; eight app screenshots were visually
inspected. Buttons and the checkbox remain reachable and multiline text wraps.
Theme validation passed all 94 checks with minimum normal-text contrast 4.55:1.

TDD regression runs established and then fixed two concrete failures: reconnecting
could revive a retired installation, and a changed review could leave automatic
deletion eligibility enabled. All six replacement persistence cases pass, as do
the existing Drive and restore-activation focused checks.

## Execution results

- Final Kotlin compilation, app/test APK builds and all **7 unit tests** passed.
  Android lint completed with **0 errors and 18 warnings** in existing manifest,
  resource and dependency/configuration code.
- The full isolated API 37 instrumentation run completed **143/147**. The four
  failures were checkout cash-entry navigation, scanning template pagination,
  search threshold validation in the appearance/text matrix and template
  retirement navigation. These cases are outside the implementation diff.
- All four failed cases passed a subsequent **4/4** focused follow-up, without
  source changes. The original full-suite result remains 143/147; those broad-run
  UI failures are recorded rather than relabelled as a clean full run.
- All replacement, Drive, restore-activation and restore-validation cases passed
  in the full run. Earlier focused runs also passed all six replacement
  persistence cases and the replacement app workflow across both appearances
  and both text scales. Eight screenshots were inspected and all 94 generated
  theme checks passed.
- Initial shared-emulator attempts were interrupted by other instrumentation.
  Final runs used a task-owned emulator, with reduced physical rendering size
  and preserved logical viewport. No Google account authorization was simulated
  as real-service evidence.

Owner-assisted real Google/device results are recorded below. TalkBack and
release-signing checks remain pending. No backup history was deleted.

## Implementation review

Independent Standards and Spec reviews compared the implementation against
ticket 21's completed commit `815e3bf`.

### Standards

One actionable finding: a publication between preview and activation could
reconfirm this writer, after which failed replacement revalidation left deletion
eligibility enabled. Activation now persists unknown identity before remote
revalidation. A real-persistence regression failed before the fix and passed
after it for changed, inaccessible, missing and malformed markers. No substantive
baseline code-smell finding was reported.
The reviewer rechecked commit `569de64` and confirmed **1 addressed, 0 remaining
actionable Standards findings**.

### Spec

The same uncertainty finding was identified and fixed. The separate real Drive
acceptance requirement remains pending for owner account/device access, as
recorded below. No additional scope creep or implementation failure was found.
The reviewer rechecked the fix and confirmed **1 addressed, 0 remaining
implementation findings**, with **1 real-service acceptance gap pending**.

The original real-service gap was subsequently exercised with the owner's
configured OAuth project/account, physical S23 and replacement emulator, as
recorded below. This does not establish release-signing continuity or access to
arbitrary previously ungranted folders. See the broader
[Drive acceptance checklist](manual-drive-backup.md#real-google-acceptance--pending-not-claimed).

The marker's content update uses the documented
[Drive files.update media endpoint](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/update).
No credentials or service secrets are stored in portable backups.

### Owner-assisted real Drive acceptance — 2026-11-14

The owner configured the Android OAuth client and personally completed Google
sign-in and Drive consent on both devices. Acceptance used the debug APK built
from committed ticket 22 source `d679db2` (SHA-256
`A42222CB2F58F2EDAFD39007D55718F38C3E647E494154DB5D1A9851B8682556`),
a physical Samsung S23 and an API 37 replacement emulator. Original app data and
APKs were saved before updating. Concurrent instrumentation was stopped by
owner-authorized coordination; both installations were verified against the
pinned APK. Later ticket 23 changes were not part of this real-service run.

The test used one owner-selected account and a dedicated folder created by the
app, `Bike part acceptance 2026-11-14 a42222`. Actual remote descendant bytes
were downloaded and verified; folder visibility alone was not used as proof.

- The S23 published revision 51 with 3 products, 3 sales, 1 unfinished cart and
  1 photo. The only business changes were creation of a labelled acceptance
  cart and one quoted line; no checkout or stock movement occurred.
- The emulator discovered complete points through real Drive, validated the
  selected revision 51 point, and restored it after the owner clicked the actual
  replacement confirmation. The app first saved its previous revision 49 as a
  before-restore recovery copy. Restored provenance retained the exact dataset,
  capture, source writer and remote manifest identities.
- Independent verification of the source portable capture and restored local
  capture found all 24 business CSV tables and the photo byte-identical. The
  actual cart UI also retained its label, SKU, quantity 1, quoted price ₹111 and
  stock of 16. Dataset/product/SKU identities were preserved; a physical printed
  QR scan was not performed.
- The owner authorized temporary emulator activation and return of the S23 to
  active afterward. During a real S23 upload, the emulator journaled a fresh
  writer and issued the marker update. Bikepart alone was force-stopped before
  local confirmation, then reopened. Its durable state remained Unknown with
  takeover pending, no coverage, no job and no success timestamp. Deletion
  eligibility therefore remained paused.
- The S23 observed the real changed marker and stopped its old-generation job
  after 6 of 26 files. Its previous covered revision, capture timestamp and
  success timestamp stayed unchanged; the interrupted job did not establish a
  completion acknowledgement. No history was deleted.
- **Check active phone** reconciled the emulator's pending takeover through the
  real marker without repeating activation. It became Active with a distinct
  writer while coverage remained empty. Its first fresh-generation backup then
  completed and was verified at revision 51, capture
  `d3e67960-1e59-4b81-91cb-4c9a1741d677`. Independent verification of that portable
  capture again matched all 24 business tables and the photo.
- After the handover, the S23 rediscovered the earlier complete points and
  revalidated the original selected capture through real Drive. The app reported
  complete, consistent validation with the current shop unchanged. This was a
  read-only validation; no second shop restore was performed.
- The emulator was retired through its actual confirmation dialog. Retirement
  waited for an already-running redundant automatic upload to complete, then
  persisted Retired with no pending job or takeover. The S23 was explicitly
  reactivated with another fresh writer,
  `48414532-9839-48aa-b272-52beaea70704`, and its previous writer's partial job
  and acknowledgement could not establish coverage for that generation.
- The test-created unfinished cart was discarded through the normal app dialog
  without a checkout or stock movement. The S23 advanced to revision 52, and its
  automatic fresh-generation backup completed with Active identity, covered
  revision 52, no pending job/takeover and no failure. The app displayed **Up to
  date**. Final capture `82323335-74a3-4b9a-9a23-9b13d91c8c2a` was independently
  verified: the other 21 business tables and photo were byte-identical; only
  the test-cart rows, shop revision and corresponding export metadata changed.
- Retirement survived an emulator app restart. The original device keep-awake
  settings and S23 screen timeout were restored, and the shared emulator was
  released to the other chat. The S23 remains the owner's active shop phone.

Private captures, original device snapshots and sanitized operational evidence
remain under `.scratch/drive-acceptance/`; they are not committed with the app.

### Additional acceptance not exercised in this run

Release-signing/reinstall continuity, a second real account, arbitrary
previously ungranted folder access, permission revocation/repair, missing or
duplicate real markers, two simultaneous real takeover requests, a physical QR
scan and TalkBack remain unverified. Deterministic persistence/transport tests
cover the relevant fault cases, but are not represented as real-service results.
The interrupted real handover above exercised a server-accepted update with
lost local confirmation and an overlapping old-client upload.

The broader manual procedure remains useful when testing these conditions:

1. Configure the owner-authorized Android OAuth client for the intended signing
   identity. On the old phone publish a complete point with photos, a receipt
   and an unfinished cart; record its capture time and remote manifest identity.
2. On the replacement, grant the selected account/folder, preview and restore
   that point. Check saved quantities, receipts, cart quotes, photos and an
   existing QR label. Review the displayed dataset, source and current target.
3. Retire the old installation with the owner. Confirm replacement activation,
   publish a new point and verify a distinct writer and fresh coverage. Attempt
   an old-client upload before uninstalling/clearing it; it must stop on the
   observed mismatch while its local edits remain possible offline.
4. Interrupt the marker-update response and reopen. Until **Check active phone**
   confirms the intended writer, require paused deletion eligibility and no
   publication acknowledgement. Check that preceding complete points still
   restore. Repeat with revoked permission, a missing/duplicate marker and a
   competing takeover; do not infer safety from an incomplete listing.
5. Record actual device/build/account-grant conditions and outcomes. A race
   after a cooperative check is not remotely fenced. Keep recovery history on
   uncertain outcomes and resolve which installation the owner will use.
