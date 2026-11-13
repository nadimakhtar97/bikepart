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
authorization. Actual retention/deletion remains ticket 23's responsibility; no
backup history is deleted by this ticket.

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
the existing Drive and restore-activation focused checks. The full-suite result
and independent review accounting will be appended after final verification.

Real Google authorization, signing/reinstall continuity, previously ungranted
folder/descendant access, replacement restore, marker-update response loss and
competing-client acceptance require the owner's configured OAuth project/account
and intended devices. They remain pending; deterministic transport checks do not
establish real-service acceptance. See the [Drive acceptance checklist](manual-drive-backup.md#real-google-acceptance--pending-not-claimed).

The marker's content update uses the documented
[Drive files.update media endpoint](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/update).
No credentials or service secrets are stored in portable backups.
