# Automatic backup and recovery coverage

Business revisions remain the durable source of truth. The app schedules unique
WorkManager work after committed shop changes and at startup. A 20-second initial
delay coalesces edits; keeping eligible work avoids repeatedly cancelling uploads.
A constrained periodic reconciliation (minimum 15 minutes) closes the commit/enqueue
and worker-finish races. These are eligibility windows, not delivery deadlines:
Doze, battery restrictions, network availability and force-stop can delay work.
Reopening the app reconciles again. No exact schedule or zero-data-loss promise is made.

Automatic work defaults to unmetered Wi-Fi. Explicitly choosing
mobile data persists in the non-exported Drive operational journal. Changing policy
replaces queued work and updates periodic constraints; the worker rechecks policy
before authorization and every Drive request. Back up now remains an explicit foreground attempt on the
owner's current connection. Background consent never opens UI: staff use Connect or
repair Drive and Back up now to grant access again. Tokens remain memory-only.

Only the verified completion manifest acknowledges coverage, for the saved account,
folder, dataset and installation generation. Interrupted jobs resume their pinned
capture. Finishing an older capture leaves newer business revisions pending and
causes another attempt. Failed uploads retain the prior complete capture timestamp.
Operational progress, failure records and network policy do not advance business
revisions or enter portable shop exports. Room stays at version 14; the existing
JSON journal accepts additive optional fields with Wi-Fi-only/no-error defaults.

[Phone replacement](active-phone-replacement.md) adds explicit writer activation
and retirement. Automatic work waits for owner action on a known phone mismatch,
retired installation or unresolved takeover, before requesting Google access.
Confirmed activation clears prior-generation jobs and coverage; startup and
periodic reconciliation schedule a fresh backup. Coverage labels do not claim
that a retired or mismatching phone is up to date.

The More screen distinguishes the last verified capture, an uploading capture,
newer pending edits, failures and up-to-date coverage. Network and storage failures
retry with backoff; account/permission consent requires foreground repair. Sales
and catalog writes do not await network operations. Changes held only on a lost
phone remain unrecoverable. Real-account consent, mobile billing and OEM background
restrictions still require physical-device acceptance.

Validation for this slice used a file-backed Room shop and an isolated API 37
emulator: all 14 focused backup/scheduler/transport/UI checks pass, as do Kotlin
compilation, unit tests, Android lint and 94 theme contrast checks. Backup controls
were inspected in light/dark at normal and 200% text. JobScheduler showed pending
timing and connectivity constraints; the real worker deferred without Wi-Fi while
cash checkout remained durable. The broad 123-test run passed 122 tests; the
existing large-text appearance-selection test timed out at ShopWorkflowTest:363
and repeated on isolation, matching the earlier local-backup run's failure.
Physical Google-account consent, OEM scheduling and screen-reader checks were not run.
