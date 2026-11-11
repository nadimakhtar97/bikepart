# Stock corrections

Ticket 08 adds separate physical-count, damage and loss actions to stocked-product
details. Each form requires a whole quantity and explanatory reason. Counts accept
zero; damage and loss remove positive quantities up to the current balance.
Descriptions, prices, SKU and original opening stock remain independent of the
current stock count. Details retain readable correction history with kind,
timestamp, reason, before/after stock and added/removed pieces or no stock change.

`Shop.correctStock` uses transaction-current stock for physical-count differences.
Audit, nullable signed movement, balance and durable business revision commit
together. An unchanged count advances the revision and records an audit event
without a movement. Operation identities deduplicate retries across reopen;
different inputs under an existing identity are rejected. History uses business
revision ordering, independent of clock changes. Schema 7 and its additive
migration are documented in [schema history](../app/schemas/README.md).

The ticket's real-persistence and actual-app boundaries are the testing seams.
`StockCorrectionsTest` covers stale observations, simultaneous counts through two
connections, simultaneous retries, damage/loss, unchanged counts, ledger
reconciliation, invalid and overflowing input, negative-result rejection,
revision overflow and interruptions at audit, balance and revision writes.
Assertions use public `Shop` snapshots/history and durable reopen; SQLite triggers
inject storage failures. A failed operation can be retried with its original
identity after storage recovers.

`CategoryMigrationTest` upgrades schemas 1–6, preserves catalog values, identity,
stock, opening history, appearance and revision, verifies version-6 template
retention, then records/reopens a correction. SQLite integrity and foreign-key
checks follow the workflows. Backup/restore has not landed, so there is no
backup codec or restore workflow to extend in this slice.

`ShopWorkflowTest.stockCorrectionsValidatePersistAndRemainUsableAcrossAppearances`
exercises the actual app with Room storage. It covers quantity/reason validation,
unchanged counts, damage/loss removal, draft recreation, original opening stock
and fresh-launch balances. Submission uses the displayed control's Compose
accessibility click action to avoid pointer timing during keyboard animation;
this does not independently verify pointer hit-testing or interception. Keyboard
captures and visible/48dp target assertions provide separate visual evidence.
The forms reuse the adopted Now in Android theme,
shared grouped surfaces, labelled fields, primary action and announced errors.
No inherited theme, type, shape, navigation style or asset decision changes.

Inspected twelve API-37 emulator captures of the count form, keyboard and history
in light/dark at normal/200% system font size, using a long product name and
maximum whole-rupee price. The captures show wrapped text, readable audit entries
and an accessible save action above the keyboard. Captures are local artifacts
under `.scratch/stock-corrections-visual/`. The existing empty-stock workflow
remains covered by the app suite. Theme validation passes all 94 generated
contrast checks (minimum normal-text contrast 4.55:1). Physical-device and manual
TalkBack checks were not performed.

Final validation passed `:app:testDebugUnitTest :app:connectedDebugAndroidTest
:app:lintDebug`: 2 unit tests and 49 instrumentation tests, with no failures or
skips. Lint reports no errors; its 12 warnings concern unchanged manifest,
dependency-version and unused-color files. Kotlin compilation and the focused
correction workflow also pass. `git diff --check` passes.

## Standards

Independent review against starting commit `248422eb54e5a4664f627220238cc4af18f593e1`
found no actionable standards findings. Shared surfaces, Material typography,
labelled input, announced/scrolling errors and pending-save controls follow the
UI guidelines. The additive, registered, exported schema and historical fixture
coverage follow the persistence rules. Vocabulary matches the glossary. No
baseline code smell rose to an actionable issue.

## Spec

The independent specification review found no missing/partial requirements,
scope creep or incorrect implementation. Transaction-current differences,
nullable zero movement, durable retry identity and atomic writes match the
ticket. Persistence coverage includes concurrency, invalid input, overflow,
reopen, reconciliation and storage interruptions; actual-app coverage exercises
validation, recreation and durable results. Both reviews inspected source while
the parent ran validation; the reviewers did not independently rerun tests.

Review totals: Standards 0, Spec 0; neither axis has an outstanding issue.
