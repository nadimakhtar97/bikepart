# Backup retention and storage

Ticket 23 adds explicit storage inspection and reviewed cleanup under More →
Backup storage. The retained set is the union of the latest seven distinct
complete captures and the latest complete capture on each Asia/Kolkata calendar
day from today through 29 days ago. Capture time determines ordering and day;
empty days contribute no point. The acknowledged preceding point and pending
upload references receive additional protection.

Inspection shows current file-data bytes in the selected folder, complete and
protected point counts, eligible capture times and an upper bound on reclaimable
bytes. Drive revisions and trash can consume additional account storage.
Only eligible complete history and its verified exclusive dependencies can be
removed. Unknown files and abandoned uploads are never inferred to be garbage.
No cleanup runs automatically. The owner reviews the consequences and confirms
**Delete reviewed history**; an interrupted plan requires **Continue reviewed
cleanup** after another inspection.

## Permission and identity requirements

Ordinary backup continues using `drive.file`. That scope can omit ungranted
files, so it cannot establish a safe garbage-collection inventory by itself.
Storage inspection and cleanup additionally request `drive.metadata.readonly`,
which can list file metadata throughout Drive. The app queries only the chosen
folder. The authorization result must explicitly include this grant; declining
it leaves cleanup paused. This does not grant broader content writes or reads.
An inaccessible completion manifest still pauses cleanup even if its metadata is
visible. The Google OAuth consent configuration must include the additional
restricted scope before real-service acceptance.

Inspection requires an owned, listable folder, all pagination to finish without
`incompleteSearch`, non-repeating pages and unique file IDs. Every complete
manifest must have a supported portable format, matching dataset/identity and
verified dependencies present in the folder inventory. Isolated Room restore
validation must also pass schema/migration, relationship and shop invariants;
consistent hashes alone cannot establish a good recovery point. A second matching full
inventory detects page churn. Missing sizes, foreign/invalid manifests,
inaccessible contents and uncertain deletion capabilities stop cleanup.

The cooperative active-phone marker is rechecked before every destructive
request. A cached active status never authorizes deletion. This retains the
[accepted manual retirement requirement](active-phone-replacement.md): a marker
check followed by a Drive request is not an atomic server-side fence.

## Dependency and interruption contract

Unchanged immutable photo blobs are reused by hash across published captures.
Every reused byte is verified against the new local capture before publication.
Retained manifests and current or archived pending upload jobs protect their
referenced IDs. Cleanup does not delete local captures, photos or business data.

The existing synced AtomicFile operational journal gains optional `manifest`
(last acknowledged point) and `cleanup` fields. A cleanup records the reviewed
manifest IDs/hashes and candidate dependency IDs/hashes before its first delete.
The manifest is deleted before its dependencies. Each request refreshes the
complete inventory, remaining manifests, retention and pending references,
checks the candidate bytes and then checks the phone marker. Within one cleanup,
unchanged verified manifests reuse their already-validated portable contents only
when the server versions of the manifest and every dependency remain identical.
Drive's monotonically increasing `version` reflects content and metadata changes.
Manifest hashes and listed dependency sizes are also rechecked. Missing or
changed versions require fresh byte verification and isolated restore validation;
same-size corruption therefore cannot hide behind the cache.

A lost delete response leaves the plan durable. After reopening, another complete
permission-checked inventory reconciles missing IDs from that reviewed plan;
HTTP 404 alone never counts as a successful deletion. Shared/pending references
are preserved. Connection changes archive the old plan and never apply it to the
new target. Unknown or damaged state prefers leaked storage to lost recovery.

Room remains version 14 and portable export format 1. Cleanup/progress and
permission metadata stay outside business revision advancement and exports.

## Verification status

The seams are those already specified in the ticket and approved for earlier
backup work: real Room/files through `Shop.driveBackup`, the actual app UI, and
only external Google transport substituted. Initial implementation preceded the
new tests; subsequent corrections use failing regressions where applicable.

`BackupRetentionTest` verifies the exact India midnight/day-30 boundary, union
overlap and empty days across reopen; shared photo deduplication; pending uploads
after failure; cleanup response loss and durable continuation; and conservative
rejection of partial/omitted dependencies, damaged manifests, uncertain owner or
delete permission and changed phone identity. It also protects the only good
point after a failed upload and rejects internally checksummed but unrestorable
stock. Business state remains unchanged.
Two further cases corrupt a shared retained photo after the first obsolete
manifest is removed, with and without remote version metadata; cleanup must stop
before removing the remaining usable older point.
The final full-suite attempt and its interruption are recorded below.

Kotlin production/test compilation and APK builds pass. All seven JVM tests pass.
Android lint reports zero errors and 18 existing warnings. All 94 generated theme
contrast checks pass, with minimum normal-text contrast 4.55:1.

The actual app workflow covers storage bytes/counts, eligible captures, cancel
without deletion, confirmation preserved through recreation and successful
cleanup without a business revision. Its light/dark × normal/200% text matrix
passed again in the final suite with the dependency-cache correction. Screenshot inspection found
clipped confirmation text at 200%; a failing scroll regression was added and
passed after making the explanation scrollable. Google authorization/transport
is substituted at the established external boundary; no real grant is implied.

### Full-suite attempt and device hold

The final APKs include the version-aware fix and compile successfully. The
156-case instrumentation run was stopped on the owner's explicit exclusive
device hold for a separate, real Drive replacement acceptance session. **20 cases
completed: 19 passed and 1 failed; 136 remain incomplete.** All seven retention
persistence cases and the four-way storage UI workflow passed in that run.

`ArchivalWorkflowTest.staffSeeLiveBlockersAndReopenArchivedDetailsAndHistory`
timed out navigating Back/Stock at line 97. It has not been isolated or rerun
because device access is held. The in-flight cart appearance test was then
deliberately stopped; the runner's resulting `Process crashed` message is the
requested force-stop, not evidence of a separate spontaneous app crash. The
full-suite attempt must not be reported as passing.

Before the hold, the focused Drive/replacement/activation regressions passed
17/17; earlier retention/transport checks passed 9/9; and both final dependency
mutation regressions passed 2/2. The newest transport-version parsing assertion
compiled but was not reached in the final full run. After the owner releases the
devices, complete the full suite and isolate the archival timeout. No additional
installation, data reset or device inspection is authorized during that hold.

## Implementation review

Independent Standards and Spec reviewers compared the staged implementation
against starting commit `d679db2`, then rechecked the correction.

### Standards

One P1 safety finding: unchanged manifest hashes and dependency sizes did not
prove retained contents were still usable. A same-size mutation during cleanup
could leave no usable recovery point. The fix ties cache reuse to unchanged
nonnull server versions for every referenced file, otherwise revalidating bytes
and isolated restore state. Both versioned and missing-version regressions pass
(2/2). No additional documented-standard violation or substantive baseline smell
was reported. **1 addressed; 0 remaining actionable findings.**

### Spec

The same P1 violated preservation of the only good point and is fixed by the
version-aware revalidation. The reviewer confirmed the two passing mutation
regressions and found no additional confirmed implementation defect or scope
creep. Real Drive cleanup/permission acceptance is still pending.
**1 addressed; 0 remaining implementation findings; 1 acceptance gap pending.**

Review totals: Standards 1 addressed / 0 remaining; Spec 1 addressed / 0 remaining
implementation findings, with real-service acceptance pending separately.

Real Google cleanup, scope consent/denial, revoked permissions, omitted descendant
access, interrupted delete responses and physical-device/TalkBack acceptance
remain pending. No real Drive history has been deleted. Controlled transport
results must not be described as real-service evidence.

## Real-service acceptance to record

1. Configure the intended Android signing identity and consent scopes. With the
   owner, publish points including shared photos, receipts and unfinished carts.
2. Inspect the exact account/folder. Record bytes/counts and expected retained
   India-day history. Confirm the extra metadata grant and test its denial.
3. Revoke a dependency or deletion permission, add an unreadable/invalid complete
   manifest, and change the active-phone marker. Each case must preserve history
   and pause cleanup. Repair access and inspect again.
4. Confirm deletion of eligible older history only. Interrupt manifest and blob
   delete responses, reopen and continue the reviewed cleanup. Verify every
   retained point still restores and shared/pending photos remain available.
5. Record actual build, device, grants, observed operations and remaining gaps.
   Keep real-service results separate from controlled transport validation.

The transport follows Google's [scope definitions](https://developers.google.com/workspace/drive/api/guides/api-specific-auth),
[files.list](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/list),
[file capabilities](https://developers.google.com/workspace/drive/api/reference/rest/v3/files)
and [files.delete](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/delete).
